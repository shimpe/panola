/*
[general]
title = "PanolaMEI"
summary = "render Panola voice(s) to an MEI music-notation document"
categories = "Notation, Utils"
related = "Classes/Panola, Classes/MSScore, Classes/PanolaMeterSplitter, Classes/PanolaMeter"
description = '''
Pure transform from Panola voice(s) + score preferences (time signature, key, clef per staff, brace
grouping) to an MEI document (a String), usable by any MEI renderer (Verovio, ...). Panola itself has
no notion of barlines / key / clef, so those are supplied here; durations are split meter-aware at the
metrical boundaries of a link::Classes/PanolaMeter:: via link::Classes/PanolaMeterSplitter:: (a note breaks
at any interior boundary stronger than the one it starts on, so a note crossing a barline or a strong
interior beat becomes several tied pieces) and the pieces are tied — this replaces the old greedy
duration decomposition. An strong::additive numerator:: (teletype::"2+2+3/8"::) sets those interior
boundaries to the group divisions, so the bar splits and beams as strong::2+2+3:: and prints an additive
meter signature; a plain teletype::"7/8":: stays ungrouped. A complete explicit teletype::*m/d:: tuplet is emitted atomically unless its span
strong::crosses a barline:: (see below), while an
strong::incomplete:: teletype::*m/d:: tuplet is now strong::completed:: the way music21 does — by splitting
the following note or rest into the teletype::<tuplet>:: bracket: the completing member(s) are spelled at
the tuplet ratio with link::Classes/PanolaDurationSpeller::, a following note ties out (its remainder is
re-emitted tied) and a following rest is split so its leading part becomes a tuplet rest. music21 never
fabricates a rest, so a strong::trailing:: incomplete tuplet (nothing follows) or a too-short follower is
left as a warned partial bracket. A complete tuplet whose span strong::crosses a barline:: is now
strong::split:: into tied per-measure teletype::<tuplet>:: brackets: a member straddling the barline is cut
there into tied sub-tuplet notes, each fragment spelled at the tuplet ratio with
link::Classes/PanolaDurationSpeller::; if a straddling fragment is not expressible at the tuplet ratio it
falls back to the whole bracket in one bar plus a warning. Eighths-and-shorter
are auto-beamed per beat, same-ratio runs become teletype::<tuplet>:: groups, and per-note
teletype::@dyn:: / teletype::@art:: properties become dynamics and articulation, while
teletype::@slur^start^:: ... teletype::@slur^end^:: spans become slurs.

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
	description = "render several Panola voices as one multi-staff MEI score (one voice per staff, top first), including meter-aware splitting-and-tying at metrical boundaries, per-beat beaming, tuplets (an incomplete teletype::*m/d:: run is completed music21-style — the following note or rest is split into the teletype::<tuplet>:: bracket with link::Classes/PanolaDurationSpeller::, a note tied out and a rest split into a tuplet rest; a trailing incomplete tuplet (nothing follows) or a too-short follower stays a warned partial bracket; a complete tuplet crossing a barline is split into tied per-measure teletype::<tuplet>:: brackets, a straddling member cut at the barline into tied sub-tuplet notes, falling back to the whole bracket plus a warning when a fragment is not expressible at the tuplet ratio), per-note dynamics/articulation, and slurs. An strong::additive meter:: numerator (teletype::\"2+2+3/8\"::) groups the bar so the splitting, per-group beaming, and the meter signature all follow the grouping, while a plain teletype::\"7/8\":: stays ungrouped."
	[classmethod.scoreAsMEI.args]
	voices = "an Array of Panola instances (one per staff, top to bottom)"
	meter = "time signature as a String, e.g. \"4/4\". An strong::additive numerator:: (teletype::\"2+2+3/8\"::) groups the bar: the durations split at the group boundaries, beam per group, and print as an strong::additive meter signature::. A plain numerator (teletype::\"7/8\"::) stays ungrouped — a plain signature with the default beaming."
	key = "key Symbol, e.g. \\Cmajor, \\Dminor, \\CsharpMinor"
	clefs = "an Array of clef Symbols (\\treble \\bass \\alto \\tenor), one per staff (nil defaults to all \\treble)"
	braces = "an Array of [firstStaff, lastStaff] 1-based ranges to brace together (nil for none)"
	[classmethod.scoreAsMEI.returns]
	what = "an MEI document (a String)"
	*/
	*scoreAsMEI {
		| voices, changes, clefs = nil, braces = nil |

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
		// meter-aware replacement for decompose on a single per-measure chunk: run the splitter and
		// flatten its SplitComponent spellings to [durToken, dots, beatsFloat] fragments. Falls back to
		// decompose for any (unexpected, for dyadic input) inexpressible piece. onsetBeats/durBeats are
		// Float quarterLength within the current measure.
		var meterPieces = { |onsetBeats, durBeats, isRest, pmeter|
			var comps = PanolaMeterSplitter.split(
				( onsetQL: PanolaRational.fromFloat(onsetBeats),
				  durationQL: PanolaRational.fromFloat(durBeats), isRest: isRest ), pmeter);
			var out = [];
			comps.do({ |c|
				var sp = c[\spelling];
				if (sp[\inexpressible]) {
					("PanolaMEI: inexpressible piece " ++ c[\durationQL].asString ++ " — using decompose").warn;
					decompose.(c[\durationQL].asFloat).do({ |pc| out = out.add([pc[0], pc[1], durToBeats.(pc[0], pc[1]), nil]) });
				} {
					sp[\components].do({ |x|
						var tup = x[\tuplets].isEmpty.if({ nil },
							{ ( num: x[\tuplets][0][\actual], numbase: x[\tuplets][0][\normal] ) });
						out = out.add([x[\meidur], x[\dots], x[\ql].asFloat, tup]);
					});
				};
			});
			out;
		};
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
		// parse a meter string into a self-contained descriptor. An additive numerator ("2+2+3/8") carries
		// groups; a plain one ("7/8") has groups nil. Every meter-dependent step consumes THIS descriptor
		// (passed as a parameter), so a future mid-piece meter change is a per-measure descriptor lookup,
		// not a rewrite. count = the display numerator; num = its sum; bb = bar length in quarterLength;
		// groupStarts = cumulative beat positions where each beam/metric group begins.
		var parseMeter = { |m|
			var parts = m.split($/), numStr = parts[0], den = parts[1].asInteger, unit = 4.0 / parts[1].asInteger;
			var groups = (numStr.indexOf($+).notNil).if({ numStr.split($+).collect({ |g| g.asInteger }) }, { nil });
			var num = groups.notNil.if({ groups.sum }, { numStr.asInteger });
			var bb = num * unit, starts;
			groups.notNil.if({
				starts = [0.0];
				groups.drop(-1).do({ |g| starts = starts.add(starts.last + (g * unit)) });
			}, {
				var gb = ((den == 8) and: { (num % 3) == 0 }).if({ 1.5 }, { 1.0 });
				starts = (0..(((bb / gb).ceil.asInteger) - 1)).collect({ |kk| kk * gb });
			});
			( count: numStr, num: num, den: den, groups: groups, bb: bb, groupStarts: starts,
				pmeter: PanolaMeter(num, den, groups) );
		};
		// compile a `changes` list [ ( measure:, meter:, key: ) ... ] into carry-forward per-measure lookups.
		var resolveChanges = { |changes|
			var srt = (changes ? [( measure: 1, meter: "4/4", key: \Cmajor )]).copy
				.sort({ |a, b| a[\measure] < b[\measure] });
			var cm = "4/4", ck = \Cmajor;
			// resolved: each entry's full (meter, key) after applying carry-forward
			srt.collect({ |c| cm = c[\meter] ? cm; ck = c[\key] ? ck; ( measure: c[\measure] ? 1, meter: cm, key: ck ) });
		};
		// returns per measure a list of records ( str: MEI, md: note-value, rest: bool, beatPos: beats-into-measure )
		var voiceToMeasures = { |events, bb, k, pmeter|
			var measures = [[]], pos = 0.0, eps = 1e-6, dynams = [], openSlur = nil, slurs = [], applySlur;
			var units, ui = 0, containers = [0.25, 0.5, 1.0, 2.0, 4.0];
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
			units = groupEvents.(events);
			while { ui < units.size } {
				var unit = units[ui], consumedDonor = false, completed = false;
				// music21-style completion: an incomplete *m/d run spells its remainder as tuplet member(s)
				// that join the bracket, by SPLITTING the following note/rest (which must exceed the remainder).
				// music21 never fabricates a rest: a trailing / no-donor / too-short-follower run stays partial.
				if ((unit[\kind] == \tuplet) and: { unit[\complete].not }) {
					var container = containers.detect({ |cc| cc >= (unit[\beats] - eps) }),
						remainder = container.notNil.if({ container - unit[\beats] }, { 0.0 }),
						donor = ((ui + 1) < units.size).if({ units[ui + 1] }, { nil }),
						inBar = container.notNil and: { (pos + container) <= (bb + eps) },
						canDonor = inBar and: { donor.notNil } and: { donor[\kind] == \normal }
							and: { (donor[\ev][\beats] + eps) >= remainder };
					if (canDonor) {
						var frecs = [], compSp = PanolaDurationSpeller.spell(PanolaRational.fromFloat(remainder)),
							dev = donor[\ev],
							compRest = dev[\rest],
							hasRemainder = (dev[\beats] - remainder) > eps,
							restEv = ( rest: true ), sub = pos;
						// (i) the unit's original members, at their written values, as tuplet-ratio records
						unit[\members].do({ |mev|
							if (mev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: sub + 1, mark: mev[\dynMark] )) };
							if ((mev[\slur] ? "") != "") { applySlur.(mev[\slur], measures.size, sub + 1) };
							frecs = frecs.add(( str: meiElement.(mev, mev[\meidur], mev[\dots], nil, k),
								md: mev[\meidur].asInteger, rest: mev[\rest], beatPos: sub,
								tup: ( num: unit[\num], numbase: unit[\numbase] ) ));
							sub = sub + mev[\beats];
						});
						// (ii) the completing member(s) from the leading remainder, split off the real donor: a note
						// donor ties out (its remainder re-emitted tied); a rest donor contributes rest member(s).
						// A note donor's @dyn/@slur belong at ITS onset (the completing member at `sub`), not
						// the tied remainder -- record here and clear from the reduced remainder in (iii).
						if (compRest.not) {
							if (dev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: sub + 1, mark: dev[\dynMark] )) };
							if ((dev[\slur] ? "") != "") { applySlur.(dev[\slur], measures.size, sub + 1) };
						};
						compSp[\components].do({ |x, ci|
							var hasPrev = (ci > 0),
								hasNext = (ci < (compSp[\components].size - 1)) or: { hasRemainder },
								ctie = compRest.if({ nil }, {
									(hasPrev and: { hasNext }).if({ "m" },
										{ hasPrev.if({ "t" }, { hasNext.if({ "i" }, { nil }) }) }) }),
								compEv = compRest.if({ restEv }, { dev });
							frecs = frecs.add(( str: meiElement.(compEv, x[\meidur], x[\dots], ctie, k),
								md: x[\meidur].asInteger, rest: compRest, beatPos: sub,
								tup: ( num: unit[\num], numbase: unit[\numbase] ) ));
							sub = sub + x[\ql].asFloat;
						});
						wrapTuplets.(frecs).do({ |r| measures[measures.size-1] = measures[measures.size-1].add(r) });
						pos = pos + container;
						if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0 };
						// (iii) reduce a note/rest donor to its remainder (tied in when a note) for the next iteration
						if (canDonor) {
							if (hasRemainder) {
								units[ui + 1] = ( kind: \normal, ev: dev.copy.put(\beats, dev[\beats] - remainder).put(\tieIn, compRest.not).put(\dynMark, nil).put(\slur, "") );
							} { consumedDonor = true };
						};
						completed = true;
					} {
						("PanolaMEI: incomplete tuplet (" ++ unit[\members].size ++ " notes, ratio " ++ unit[\num] ++ ":" ++ unit[\numbase] ++ ") — emitting a partial bracket").warn;
					};
				};
				if (completed.not) {
					if (unit[\kind] == \tuplet) {
						// a complete *m/d tuplet is atomic UNLESS its span crosses a barline: then a straddling
						// member is cut at the barline into tied sub-tuplet notes and each per-measure slice is
						// bracketed on its own (music21-style makeTupletBrackets over split fragments).
						var tbeats = unit[\beats],
							crosses = (tbeats > ((bb - pos) + eps)) and: { (bb - pos) > eps },
							ratio = ( num: unit[\num], numbase: unit[\numbase] ),
							// dyn/slur are collected here during the walk and only committed if the split
							// succeeds, so a fallback (which re-adds them atomically) never double-adds.
							pendDyn = [], pendSlur = [],
							// build per-measure record buckets by walking members, splitting a straddling
							// member at the barline. Returns nil if any straddling fragment is inexpressible
							// or not a single component at the unit's ratio -> caller falls back.
							buildSplit = {
								var buckets = [[]], sub = pos, ok = true, speller = PanolaDurationSpeller.new,
									fragAt = { |d|   // spell d beats -> (meidur, dots) at the unit's ratio, or nil
										var sp = speller.spell(PanolaRational.fromFloat(d)), c;
										(sp[\inexpressible] or: { sp[\components].size != 1 }).if({ nil }, {
											c = sp[\components][0];
											((c[\tuplets].size == 1)
												and: { c[\tuplets][0][\actual] == ratio[\num] }
												and: { c[\tuplets][0][\normal] == ratio[\numbase] }).if(
												{ ( meidur: c[\meidur], dots: c[\dots] ) }, { nil }); });
									};
								unit[\members].do({ |mev|
									var mb = mev[\beats], firstPiece = true;
									// dyn/slur at the member onset (measure = base + current bucket index)
									if (mev[\dynMark].notNil) { pendDyn = pendDyn.add(( measure: measures.size + (buckets.size - 1), tstamp: sub + 1, mark: mev[\dynMark] )) };
									if ((mev[\slur] ? "") != "") { pendSlur = pendSlur.add([ mev[\slur], measures.size + (buckets.size - 1), sub + 1 ]) };
									while { (mb > eps) and: { ok } } {
										var room = bb - sub;
										(mb <= (room + eps)).if({
											// the (remaining) member fits in this bar
											var md = firstPiece.if({ mev[\meidur] }, { var f = fragAt.(mb); f.isNil.if({ ok = false; nil }, { f[\meidur] }) }),
												dt = firstPiece.if({ mev[\dots] }, { var f = fragAt.(mb); f.isNil.if({ 0 }, { f[\dots] }) }),
												tie = firstPiece.if({ nil }, { "t" });
											if (ok) {
												buckets[buckets.size - 1] = buckets[buckets.size - 1].add(
													( str: meiElement.(mev, md, dt, tie, k), md: md.asInteger, rest: mev[\rest], beatPos: sub, tup: ratio ));
											};
											sub = sub + mb; mb = 0;
											if ((bb - sub) < eps) { buckets = buckets.add([]); sub = 0.0 };
										}, {
											// the member straddles the barline: emit `room` beats here (tie), cross over
											var f = fragAt.(room);
											f.isNil.if({ ok = false }, {
												var tie = firstPiece.if({ "i" }, { "m" });
												buckets[buckets.size - 1] = buckets[buckets.size - 1].add(
													( str: meiElement.(mev, f[\meidur], f[\dots], tie, k), md: f[\meidur].asInteger, rest: mev[\rest], beatPos: sub, tup: ratio ));
												buckets = buckets.add([]); mb = mb - room; sub = 0.0; firstPiece = false;
											});
										});
									};
								});
								ok.if({ buckets }, { nil });
							},
							split = crosses.if({ buildSplit.value }, { nil });
						if (split.notNil) {
							// commit dyn/slur (only now that the split succeeded), then emit each per-measure
							// bucket through wrapTuplets, advancing pos across the barlines it crossed
							pendDyn.do({ |dd| dynams = dynams.add(dd) });
							pendSlur.do({ |ss| applySlur.(ss[0], ss[1], ss[2]) });
							split.do({ |bucket, bi|
								wrapTuplets.(bucket).do({ |r| measures[measures.size - 1] = measures[measures.size - 1].add(r) });
								if (bi < (split.size - 1)) { measures = measures.add([]) };
							});
							pos = (pos + tbeats) - ((split.size - 1) * bb);
						} {
							// non-crossing, or a fragment could not be spelled: atomic bracket (+ warn if it crosses).
							// give each tuplet member its real sub-tuplet beat offset, so dynamics/slur endpoints
							// land on the right note (a slur inside one tuplet must not collapse to a point).
							unit[\members].inject(0.0, { |macc, mev|
								var mts = pos + macc + 1;
								if (mev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: mts, mark: mev[\dynMark] )) };
								if ((mev[\slur] ? "") != "") { applySlur.(mev[\slur], measures.size, mts) };
								macc + mev[\beats];
							});
							if (crosses) {
								("PanolaMEI: tuplet crosses a barline; kept whole in bar " ++ measures.size ++ " (fragment not expressible at the tuplet ratio)").warn;
							};
							measures[measures.size-1] = measures[measures.size-1].add(
								( str: tupletMEI.(unit, k), md: 0, rest: false, beatPos: pos, tuplet: true ));
							pos = pos + tbeats;
							if (pos >= (bb - eps)) { measures = measures.add([]); pos = (pos - bb).max(0.0) };
						};
					} {
						var ev = unit[\ev];
						var remaining = ev[\beats], firstFrag = true;
							if (ev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: pos + 1, mark: ev[\dynMark] )) };
							if ((ev[\slur] ? "") != "") { applySlur.(ev[\slur], measures.size, pos + 1) };
						while { remaining > eps } {
							var take = (bb - pos).min(remaining), crosses = remaining > ((bb - pos) + eps);
							var lastFrag = crosses.not, pieces = meterPieces.(pos, take, ev[\rest], pmeter), subpos = pos, frecs = [];
							pieces.do({ |pc, c|
								var isFirst = firstFrag and: { c == 0 }, isLast = lastFrag and: { c == (pieces.size - 1) },
									hasPrev = (ev[\tieIn] == true) or: { isFirst.not }, hasNext = isLast.not, tie = nil;
								if (ev[\rest].not) {
									tie = (hasPrev and: { hasNext }).if({ "m" },
										{ hasPrev.if({ "t" }, { hasNext.if({ "i" }, { nil }) }) });
								};
								frecs = frecs.add(( str: meiElement.(ev, pc[0], pc[1], tie, k), md: pc[0].asInteger,
									rest: ev[\rest], beatPos: subpos, tup: pc[3] ));
								subpos = subpos + pc[2];
							});
							wrapTuplets.(frecs).do({ |r| measures[measures.size-1] = measures[measures.size-1].add(r) });
							pos = pos + take; remaining = remaining - take; firstFrag = false;
							if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0 };
						};
					};
				};
				ui = consumedDonor.if({ ui + 2 }, { ui + 1 });
			};
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
		// that share a beam group in <beam> ... </beam>. groupStarts: the cumulative beat positions
		// where each metric/beam group begins (uniform for a plain meter, per-group for an additive one).
		var beamMeasure = { |records, groupStarts|
			var result = "", i = 0, eps = 1e-6,
				groupOf = { |bp| (groupStarts.count({ |s| s <= (bp + eps) }) - 1) };
			while { i < records.size } {
				var rec = records[i], beamable = rec[\rest].not and: { rec[\md] >= 8 };
				if (beamable) {
					var grp = groupOf.(rec[\beatPos]), run = [rec], j = i + 1;
					while { (j < records.size) and: { records[j][\rest].not and: { (records[j][\md] >= 8) and: { groupOf.(records[j][\beatPos]) == grp } } } } {
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
		// group consecutive fragment-records that share a tuplet ratio into one <tuplet> bracket record
		// (beamed inside via beamRun); records with a nil ratio pass through unchanged. This is music21's
		// makeTupletBrackets over split fragments.
		var wrapTuplets = { |recs|
			var out = [], i = 0;
			while { i < recs.size } {
				var rec = recs[i], tup = rec[\tup];
				if (tup.notNil) {
					var run = [rec], j = i + 1;
					while { (j < recs.size) and: { recs[j][\tup].notNil }
						and: { recs[j][\tup][\num] == tup[\num] } and: { recs[j][\tup][\numbase] == tup[\numbase] } } {
						run = run.add(recs[j]); j = j + 1;
					};
					out = out.add(( str: "<tuplet num=\"" ++ tup[\num] ++ "\" numbase=\"" ++ tup[\numbase] ++ "\">"
						++ beamRun.(run) ++ "</tuplet>", md: 0, rest: false, beatPos: run[0][\beatPos], tuplet: true ));
					i = j;
				} { out = out.add(rec); i = i + 1 };
			};
			out;
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
				// a degenerate *m/d ratio (after gcd, one side is 1) is not a real tuplet: 1:d or m:1 has
				// integer duration, so route it through \normal (splits-and-ties at barlines) not \tuplet.
				var g = ev[\mult].gcd(ev[\div]);
				var degenerate = ((ev[\div] / g) == 1) or: { (ev[\mult] / g) == 1 };
				if (((ev[\mult] == 1) and: { ev[\div] == 1 }) or: { degenerate }) {
					units = units.add(( kind: \normal, ev: ev )); i = i + 1;
				} {
					var m = ev[\mult], d = ev[\div], members = [], acc = 0.0, closed = false;
					while { (i < events.size) and: { (events[i][\mult] == m) and: { events[i][\div] == d } } and: { closed.not } } {
						members = members.add(events[i]); acc = acc + events[i][\beats]; i = i + 1;
						if (containers.any({ |c| (acc - c).abs < eps })) { closed = true };
					};
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
		var perVoice, nm, body = "";
		var resolved = resolveChanges.(changes);
		var atFor = { |i| var r = resolved.select({ |c| c[\measure] <= i }).last;
			r ? ( measure: 1, meter: "4/4", key: \Cmajor ) };
		var meterFor = { |i| parseMeter.(atFor.(i)[\meter]) };
		var keyFor = { |i| atFor.(i)[\key] };
		var m0 = meterFor.(1), k0 = keyFor.(1);
		clefs = clefs ? voices.collect({ \treble });
		perVoice = voices.collect({ |p| voiceToMeasures.(annotateExpression.(eventsOf.(p)), m0[\bb], k0, m0[\pmeter]) });
		nm = perVoice.collect({ |v| v[\measures].size }).maxItem;
		perVoice = perVoice.collect({ |v| while { v[\measures].size < nm } { v[\measures] = v[\measures].add(emptyRest.(m0[\bb])) }; v });
		nm.do({ |i|
			body = body ++ "<measure n=\"" ++ (i+1) ++ "\">";
			perVoice.do({ |v, s| body = body ++ "<staff n=\"" ++ (s+1) ++ "\"><layer n=\"1\">" ++ beamMeasure.(v[\measures][i], m0[\groupStarts]) ++ "</layer></staff>" });
			perVoice.do({ |v, s|
				v[\dynams].select({ |dm| dm[\measure] == (i+1) }).do({ |dm|
					var tsv = dm[\tstamp], tss = (tsv.frac < 1e-6).if({ tsv.asInteger.asString }, { tsv.round(0.0001).asString });
					body = body ++ "<dynam tstamp=\"" ++ tss ++ "\" staff=\"" ++ (s+1) ++ "\">" ++ dm[\mark] ++ "</dynam>";
				});
			});
			perVoice.do({ |v, s|
				v[\slurs].select({ |sl| sl[\startMeasure] == (i+1) }).do({ |sl|
					var t1 = sl[\startTstamp], t2 = sl[\endTstamp], dm = sl[\endMeasure] - sl[\startMeasure];
					var t1s = (t1.frac < 1e-6).if({ t1.asInteger.asString }, { t1.round(0.0001).asString });
					var t2s = (t2.frac < 1e-6).if({ t2.asInteger.asString }, { t2.round(0.0001).asString });
					body = body ++ "<slur tstamp=\"" ++ t1s ++ "\" tstamp2=\"" ++ dm ++ "m+" ++ t2s ++ "\" staff=\"" ++ (s+1) ++ "\"/>";
				});
			});
			body = body ++ "</measure>";
		});
		^("<?xml version=\"1.0\" encoding=\"UTF-8\"?><mei xmlns=\"http://www.music-encoding.org/ns/mei\" meiversion=\"4.0.0\"><music><body><mdiv><score>"
			++ "<scoreDef meter.count=\"" ++ m0[\count] ++ "\" meter.unit=\"" ++ m0[\den] ++ "\" key.sig=\"" ++ keyToSig.(k0) ++ "\">"
			++ staffGrp.(voices.size, clefs, braces) ++ "</scoreDef><section>" ++ body ++ "</section></score></mdiv></body></music></mei>");
	}
}
