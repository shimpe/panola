/*
[general]
title = "PanolaMEI"
summary = "render Panola voice(s) to an MEI music-notation document"
categories = "Notation, Utils"
related = "Classes/Panola, Classes/MSScore"
description = '''
Pure transform from Panola voice(s) + score preferences (time signature, key, clef per staff, brace
grouping) to an MEI document (a String), usable by any MEI renderer (Verovio, ...). Panola itself has
no notion of barlines / key / clef, so those are supplied here; notes crossing a barline are split and
tied, eighths-and-shorter are auto-beamed per beat, same-ratio runs become teletype::<tuplet>:: groups,
and per-note teletype::@dyn:: / teletype::@art:: properties become dynamics and articulation.

Typical use:
code::
PanolaMEI.scoreAsMEI([Panola("c5_4 e g a"), Panola("c3_2 g2")],
    meter: "4/4", key: \Cmajor, clefs: [\treble, \bass], braces: [[1,2]]);
::
Also reachable as teletype::aPanola.asMEI(meter, key, clef):: (see link::Classes/Panola::).
'''
*/
PanolaMEI {

	/*
	[classmethod.scoreAsMEI]
	description = "render several Panola voices as one multi-staff MEI score (one voice per staff, top first), including ties across barlines, per-beat beaming, tuplets, and per-note dynamics/articulation."
	[classmethod.scoreAsMEI.args]
	voices = "an Array of Panola instances (one per staff, top to bottom)"
	meter = "time signature as a String, e.g. \"4/4\""
	key = "key Symbol, e.g. \\Cmajor, \\Dminor, \\CsharpMinor"
	clefs = "an Array of clef Symbols (\\treble \\bass \\alto \\tenor), one per staff (nil defaults to all \\treble)"
	braces = "an Array of [firstStaff, lastStaff] 1-based ranges to brace together (nil for none)"
	[classmethod.scoreAsMEI.returns]
	what = "an MEI document (a String)"
	*/
	*scoreAsMEI {
		| voices, meter = "4/4", key = \Cmajor, clefs = nil, braces = nil |

		// ---- pure helpers -------------------------------------------------
		var parseOne = { |s|
			var pname = s[0].asString, i = 1, mod = "", octstr, accid = nil;
			while { (i < s.size) and: { "#x-".includes(s[i]) } } { mod = mod ++ s[i].asString; i = i + 1 };
			octstr = s.copyRange(i, s.size - 1);
			if (mod == "#") { accid = "s" }; if (mod == "x") { accid = "x" };
			if (mod == "-") { accid = "f" }; if (mod == "--") { accid = "ff" };
			[pname, accid, octstr.asInteger];
		};
		var parseName = { |s|
			if (s == "r") { (rest: true) } {
				if (s[0] == $<) {
					var ps = s.copyRange(1, s.size - 2).split($ ).collect({ |n| parseOne.(n) });
					(rest: false, pnames: ps.collect(_[0]), accids: ps.collect(_[1]), octs: ps.collect(_[2]))
				} {
					var p = parseOne.(s);
					(rest: false, pnames: [p[0]], accids: [p[1]], octs: [p[2]])
				}
			}
		};
		var parseDur = { |s|
			// notationdurationPattern form: "_<value>.0( .)*(\*<mult>/<div>)?", e.g. "_8.0 .*2/3"
			var afterU = s.copyRange(1, s.size - 1);
			var starIdx = afterU.indexOf($*);
			var durPart = if (starIdx.notNil) { afterU.copyRange(0, starIdx - 1) } { afterU };
			var ratioPart = if (starIdx.notNil) { afterU.copyRange(starIdx + 1, afterU.size - 1) } { "1/1" };
			var tokens = durPart.split($ );                 // ["8.0", "."]
			var value = tokens[0].asFloat.asInteger;        // 8
			var dots = tokens.size - 1;                     // count of space-separated "."
			var ratio = ratioPart.split($/);                // ["2", "3"]
			[value, dots, ratio[0].asInteger, ratio[1].asInteger];   // [meidur, dots, mult, div]
		};
		var vals = [[1,0,4.0],[2,1,3.0],[2,0,2.0],[4,1,1.5],[4,0,1.0],[8,1,0.75],[8,0,0.5],[16,0,0.25]];
		var decompose = { |beats|
			var out = [], remaining = beats, eps = 1e-6;
			while { remaining > eps } {
				var found = false;
				vals.do({ |v| if (found.not and: { v[2] <= (remaining + eps) }) { out = out.add([v[0],v[1]]); remaining = remaining - v[2]; found = true } });
				if (found.not) { remaining = 0 };
			};
			out;
		};
		var durToBeats = { |md, dt| (4 / md) * (2 - (1 / (2 ** dt))) };
		var artCode = { |name|
			IdentityDictionary[
				\staccato->"stacc", \stacc->"stacc", \staccatissimo->"stacciss", \stacciss->"stacciss",
				\accent->"acc", \acc->"acc", \tenuto->"ten", \ten->"ten",
				\marcato->"marc", \marc->"marc", \spiccato->"spicc", \spicc->"spicc"
			][name.asString.asSymbol];
		};
		// per event: articStr (space-separated MEI artic codes) + dynMark (dynamic to emit here, or nil).
		// articulation set: "name:on"/"name:off" toggles it (on change); a bare name adds to this note only.
		var annotateExpression = { |events|
			var artSet = Set[], prevArt = "", prevDyn = "";
			events.do({ |ev|
				var art = ev[\art] ? "", dyn = ev[\dyn] ? "", noteSet;
				if ((art != prevArt) and: { art.includes($:) }) {
					var parts = art.split($:), code = artCode.(parts[0]);
					if (code.notNil) {
						(parts[1] == "on").if({ artSet = artSet.add(code) }, { artSet.remove(code) });
					} { ("PanolaMEI: unknown articulation '" ++ parts[0] ++ "'").warn };
				};
				prevArt = art;
				noteSet = artSet.copy;
				if ((art != "") and: { art.includes($:).not }) {
					var code = artCode.(art);
					if (code.notNil) { noteSet = noteSet.add(code) } { ("PanolaMEI: unknown articulation '" ++ art ++ "'").warn };
				};
				ev[\articStr] = noteSet.asArray.sort.join(" ");
				ev[\dynMark] = ((dyn != prevDyn) and: { dyn != "" }).if({ dyn }, { nil });
				prevDyn = dyn;
			});
			events;
		};
		var keysig = IdentityDictionary[
			\cmajor->[0,\n], \aminor->[0,\n], \gmajor->[1,\s], \eminor->[1,\s], \dmajor->[2,\s], \bminor->[2,\s],
			\amajor->[3,\s], \fsharpminor->[3,\s], \emajor->[4,\s], \csharpminor->[4,\s], \bmajor->[5,\s], \gsharpminor->[5,\s],
			\fmajor->[1,\f], \dminor->[1,\f], \bflatmajor->[2,\f], \gminor->[2,\f], \eflatmajor->[3,\f], \cminor->[3,\f],
			\aflatmajor->[4,\f], \fminor->[4,\f], \dflatmajor->[5,\f], \bflatminor->[5,\f]
		];
		var sharpOrder = ["f","c","g","d","a","e","b"], flatOrder = ["b","e","a","d","g","c","f"];
		var keyKey = { |k| k.asString.toLower.asSymbol };
		var keyAlters = { |k|
			var kc = keysig[keyKey.(k)], d = IdentityDictionary.new;
			var order = (kc[1] == \s).if({ sharpOrder }, { flatOrder });
			kc[0].do({ |i| d[order[i].asSymbol] = (kc[1] == \s).if({"s"},{"f"}) });
			d;
		};
		var keyToSig = { |k| var kc = keysig[keyKey.(k)]; (kc[0] == 0).if({"0"},{ kc[0].asString ++ (kc[1]==\s).if({"s"},{"f"}) }) };
		var accidInKey = { |pname, accid, k|
			var alt = keyAlters.(k)[pname.asSymbol];
			if (accid == alt) { nil } { if (accid.isNil) { alt.notNil.if({"n"},{nil}) } { accid } };
		};
		var durAttrs = { |md, dt| " dur=\"" ++ md ++ "\"" ++ (dt > 0).if({ " dots=\"" ++ dt ++ "\"" }, {""}) };
		var accidS = { |a| a.notNil.if({ " accid=\"" ++ a ++ "\"" }, {""}) };
		var meiElement = { |ev, md, dt, tie, k|
			var ts = tie.notNil.if({ " tie=\"" ++ tie ++ "\"" }, {""});
			// articulation only on a whole note or the first tied fragment (not on continuations)
			var aa = (((ev[\articStr] ? "") != "") and: { tie.isNil or: { tie == "i" } }).if({ " artic=\"" ++ ev[\articStr] ++ "\"" }, { "" });
			if (ev[\rest]) { "<rest" ++ durAttrs.(md,dt) ++ "/>" } {
				if (ev[\pnames].size == 1) {
					"<note" ++ durAttrs.(md,dt) ++ aa ++ " oct=\"" ++ ev[\octs][0] ++ "\" pname=\"" ++ ev[\pnames][0] ++ "\"" ++ accidS.(accidInKey.(ev[\pnames][0], ev[\accids][0], k)) ++ ts ++ "/>"
				} {
					var inner = "";
					ev[\pnames].size.do({ |c| inner = inner ++ "<note oct=\"" ++ ev[\octs][c] ++ "\" pname=\"" ++ ev[\pnames][c] ++ "\"" ++ accidS.(accidInKey.(ev[\pnames][c], ev[\accids][c], k)) ++ ts ++ "/>" });
					"<chord" ++ durAttrs.(md,dt) ++ aa ++ ">" ++ inner ++ "</chord>"
				}
			}
		};
		var barBeats = { |m| var p = m.split($/); p[0].asInteger * (4.0 / p[1].asInteger) };
		// returns per measure a list of records ( str: MEI, md: note-value, rest: bool, beatPos: beats-into-measure )
		var voiceToMeasures = { |events, bb, k|
			var measures = [[]], pos = 0.0, eps = 1e-6, dynams = [], openSlur = nil, slurs = [], applySlur;
			// pair @slur^start/end/endstart^ into slur markers. one open slur at a time (no nesting);
			// endstart closes the open slur and opens a new one at the same note. m/ts = 1-based measure
			// and beat of the marker note. warn + recover on any mismatch.
			applySlur = { |slurVal, m, ts|
				case
				{ slurVal == "start" } {
					if (openSlur.notNil) { "PanolaMEI: slur start while a slur is open; the previous one is dropped".warn };
					openSlur = ( measure: m, tstamp: ts );
				}
				{ slurVal == "end" } {
					if (openSlur.notNil) {
						slurs = slurs.add(( startMeasure: openSlur[\measure], startTstamp: openSlur[\tstamp], endMeasure: m, endTstamp: ts ));
						openSlur = nil;
					} { "PanolaMEI: slur end with no open slur; ignored".warn };
				}
				{ slurVal == "endstart" } {
					if (openSlur.notNil) {
						slurs = slurs.add(( startMeasure: openSlur[\measure], startTstamp: openSlur[\tstamp], endMeasure: m, endTstamp: ts ));
					} { "PanolaMEI: slur endstart with no open slur; only opening a new one".warn };
					openSlur = ( measure: m, tstamp: ts );
				}
				{ true } { if (slurVal != "") { ("PanolaMEI: unknown slur value '" ++ slurVal ++ "'").warn } };
			};
			groupEvents.(events).do({ |unit|
				if (unit[\kind] == \tuplet) {
					// tuplet groups are atomic: never decomposed or split-and-tied across a barline
					var tbeats = unit[\beats];
						unit[\members].do({ |mev| if (mev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: pos + 1, mark: mev[\dynMark] )) } });
						unit[\members].do({ |mev| if ((mev[\slur] ? "") != "") { applySlur.(mev[\slur], measures.size, pos + 1) } });
					if ((tbeats > ((bb - pos) + eps)) and: { (bb - pos) > eps }) {
						("PanolaMEI: tuplet crosses a barline; kept whole in bar " ++ measures.size ++ " (split tuplets unsupported)").warn;
					};
					measures[measures.size-1] = measures[measures.size-1].add(
						( str: tupletMEI.(unit, k), md: 0, rest: false, beatPos: pos, tuplet: true ));
					pos = pos + tbeats;
					if (pos >= (bb - eps)) { measures = measures.add([]); pos = (pos - bb).max(0.0) };
				} {
					var ev = unit[\ev];
					var remaining = ev[\beats], firstFrag = true;
						if (ev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: pos + 1, mark: ev[\dynMark] )) };
						if ((ev[\slur] ? "") != "") { applySlur.(ev[\slur], measures.size, pos + 1) };
					while { remaining > eps } {
						var take = (bb - pos).min(remaining), crosses = remaining > ((bb - pos) + eps);
						var lastFrag = crosses.not, pieces = decompose.(take), subpos = pos;
						pieces.do({ |pc, c|
							var isFirst = firstFrag and: { c == 0 }, isLast = lastFrag and: { c == (pieces.size - 1) }, tie = nil;
							if (ev[\rest].not and: { (isFirst and: { isLast }).not }) {
								tie = isFirst.if({"i"},{ isLast.if({"t"},{"m"}) });
							};
							measures[measures.size-1] = measures[measures.size-1].add(
								( str: meiElement.(ev, pc[0], pc[1], tie, k), md: pc[0], rest: ev[\rest], beatPos: subpos ));
							subpos = subpos + durToBeats.(pc[0], pc[1]);
						});
						pos = pos + take; remaining = remaining - take; firstFrag = false;
						if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0 };
					};
				};
			});
			if (measures[measures.size-1].size == 0) { measures = measures.copyRange(0, measures.size - 2) };
			if (openSlur.notNil) { "PanolaMEI: unclosed slur at the end of a voice; dropped".warn };
			( measures: measures, dynams: dynams, slurs: slurs );
		};
		var clefMap = IdentityDictionary[\treble->["G","2"], \bass->["F","4"], \alto->["C","3"], \tenor->["C","4"]];
		var emptyRest = { |bb|
			var recs = [], p = 0.0;
			decompose.(bb).do({ |pc|
				recs = recs.add(( str: "<rest" ++ durAttrs.(pc[0],pc[1]) ++ "/>", md: pc[0], rest: true, beatPos: p ));
				p = p + durToBeats.(pc[0], pc[1]);
			});
			recs;
		};
		// join a measure's records to MEI, wrapping runs of >=2 beamable notes (dur>=8, not rest)
		// that share a beat-group in <beam> ... </beam>. groupBeats: 1 beat (simple) or 1.5 (compound /8).
		var beamMeasure = { |records, groupBeats|
			var result = "", i = 0;
			while { i < records.size } {
				var rec = records[i], beamable = rec[\rest].not and: { rec[\md] >= 8 };
				if (beamable) {
					var grp = (rec[\beatPos] / groupBeats).floor, run = [rec], j = i + 1;
					while { (j < records.size) and: { records[j][\rest].not and: { (records[j][\md] >= 8) and: { (records[j][\beatPos] / groupBeats).floor == grp } } } } {
						run = run.add(records[j]); j = j + 1;
					};
					if (run.size >= 2) { result = result ++ "<beam>" ++ run.collect({ |r| r[\str] }).join ++ "</beam>" } { result = result ++ run[0][\str] };
					i = j;
				} { result = result ++ rec[\str]; i = i + 1 };
			};
			result;
		};
		// beam consecutive beamable members (dur >= 8, not rest); used inside a tuplet
		var beamRun = { |recs|
			var result = "", i = 0;
			while { i < recs.size } {
				var rec = recs[i], beamable = rec[\rest].not and: { rec[\md] >= 8 };
				if (beamable) {
					var run = [rec], j = i + 1;
					while { (j < recs.size) and: { recs[j][\rest].not and: { recs[j][\md] >= 8 } } } { run = run.add(recs[j]); j = j + 1 };
					if (run.size >= 2) { result = result ++ "<beam>" ++ run.collect({ |r| r[\str] }).join ++ "</beam>" } { result = result ++ run[0][\str] };
					i = j;
				} { result = result ++ rec[\str]; i = i + 1 };
			};
			result;
		};
		// one tuplet group -> <tuplet num numbase> at written values, beamable members beamed inside
		var tupletMEI = { |unit, k|
			var recs = unit[\members].collect({ |ev|
				( str: meiElement.(ev, ev[\meidur], ev[\dots], nil, k), md: ev[\meidur], rest: ev[\rest] );
			});
			"<tuplet num=\"" ++ unit[\num] ++ "\" numbase=\"" ++ unit[\numbase] ++ "\">" ++ beamRun.(recs) ++ "</tuplet>";
		};
		// split a voice's events into plain-note units and tuplet-group units. A tuplet run (same
		// mult/div != 1/1) closes when its accumulated actual beats fill a power-of-2 container
		// (0.25/0.5/1/2/4); an unclosed run is emitted as a partial tuplet + warning.
		var groupEvents = { |events|
			var units = [], i = 0, eps = 1e-6, containers = [0.25, 0.5, 1.0, 2.0, 4.0];
			while { i < events.size } {
				var ev = events[i];
				if ((ev[\mult] == 1) and: { ev[\div] == 1 }) {
					units = units.add(( kind: \normal, ev: ev )); i = i + 1;
				} {
					var m = ev[\mult], d = ev[\div], members = [], acc = 0.0, closed = false;
					while { (i < events.size) and: { (events[i][\mult] == m) and: { events[i][\div] == d } } and: { closed.not } } {
						members = members.add(events[i]); acc = acc + events[i][\beats]; i = i + 1;
						if (containers.any({ |c| (acc - c).abs < eps })) { closed = true };
					};
					if (closed.not) { ("PanolaMEI: incomplete tuplet (" ++ members.size ++ " notes, ratio " ++ d ++ ":" ++ m ++ ") — emitting a partial bracket").warn };
					units = units.add(( kind: \tuplet, num: d, numbase: m, members: members, beats: acc, complete: closed ));
				};
			};
			units;
		};
		var staffGrp = { |nst, cl, br|
			var defs = (1..nst).collect({ |n| "<staffDef n=\"" ++ n ++ "\" lines=\"5\" clef.shape=\"" ++ clefMap[cl[n-1]][0] ++ "\" clef.line=\"" ++ clefMap[cl[n-1]][1] ++ "\"/>" });
			var out = "", n = 1;
			br = br ? [];
			while { n <= nst } {
				var grp = br.detect({ |b| b[0] == n });
				if (grp.notNil) {
					out = out ++ "<staffGrp symbol=\"brace\" bar.thru=\"true\">" ++ (grp[0]..grp[1]).collect({ |c| defs[c-1] }).join ++ "</staffGrp>";
					n = grp[1] + 1;
				} { out = out ++ defs[n-1]; n = n + 1 };
			};
			"<staffGrp>" ++ out ++ "</staffGrp>";
		};
		var eventsOf = { |panola|
			var names = panola.notationnotePattern.asStream.all;
			var durs = panola.notationdurationPattern.asStream.all;
			var beats = panola.durationPattern.asStream.all;
			var dyns = panola.customPropertyPattern("dyn", "").asStream.all;
			var arts = panola.customPropertyPattern("art", "").asStream.all;
			var slurs = panola.customPropertyPattern("slur", "").asStream.all;
			names.collect({ |nm, i|
				var e = parseName.(nm), d = parseDur.(durs[i]);
				e[\meidur] = d[0]; e[\dots] = d[1]; e[\mult] = d[2]; e[\div] = d[3]; e[\beats] = beats[i];
				e[\dyn] = dyns[i].asString; e[\art] = arts[i].asString; e[\slur] = slurs[i].asString;
				e;
			});
		};

		// ---- body ---------------------------------------------------------
		var bb, perVoice, nm, mp, groupBeats, body = "";
		clefs = clefs ? voices.collect({ \treble });
		bb = barBeats.(meter);
		mp = meter.split($/);
		groupBeats = ((mp[1].asInteger == 8) and: { (mp[0].asInteger % 3) == 0 }).if({ 1.5 }, { 1.0 });
		perVoice = voices.collect({ |p| voiceToMeasures.(annotateExpression.(eventsOf.(p)), bb, key) });
		nm = perVoice.collect({ |v| v[\measures].size }).maxItem;
		perVoice = perVoice.collect({ |v| while { v[\measures].size < nm } { v[\measures] = v[\measures].add(emptyRest.(bb)) }; v });
		nm.do({ |i|
			body = body ++ "<measure n=\"" ++ (i+1) ++ "\">";
			perVoice.do({ |v, s| body = body ++ "<staff n=\"" ++ (s+1) ++ "\"><layer n=\"1\">" ++ beamMeasure.(v[\measures][i], groupBeats) ++ "</layer></staff>" });
			perVoice.do({ |v, s|
				v[\dynams].select({ |dm| dm[\measure] == (i+1) }).do({ |dm|
					var tsv = dm[\tstamp], tss = (tsv.frac < 1e-6).if({ tsv.asInteger.asString }, { tsv.asString });
					body = body ++ "<dynam tstamp=\"" ++ tss ++ "\" staff=\"" ++ (s+1) ++ "\">" ++ dm[\mark] ++ "</dynam>";
				});
			});
			perVoice.do({ |v, s|
				v[\slurs].select({ |sl| sl[\startMeasure] == (i+1) }).do({ |sl|
					var t1 = sl[\startTstamp], t2 = sl[\endTstamp], dm = sl[\endMeasure] - sl[\startMeasure];
					var t1s = (t1.frac < 1e-6).if({ t1.asInteger.asString }, { t1.asString });
					var t2s = (t2.frac < 1e-6).if({ t2.asInteger.asString }, { t2.asString });
					body = body ++ "<slur tstamp=\"" ++ t1s ++ "\" tstamp2=\"" ++ dm ++ "m+" ++ t2s ++ "\" staff=\"" ++ (s+1) ++ "\"/>";
				});
			});
			body = body ++ "</measure>";
		});
		^("<?xml version=\"1.0\" encoding=\"UTF-8\"?><mei xmlns=\"http://www.music-encoding.org/ns/mei\" meiversion=\"4.0.0\"><music><body><mdiv><score>"
			++ "<scoreDef meter.count=\"" ++ mp[0] ++ "\" meter.unit=\"" ++ mp[1] ++ "\" key.sig=\"" ++ keyToSig.(key) ++ "\">"
			++ staffGrp.(voices.size, clefs, braces) ++ "</scoreDef><section>" ++ body ++ "</section></score></mdiv></body></music></mei>");
	}
}
