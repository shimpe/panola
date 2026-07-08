/*
[general]
title = "PanolaMEI"
summary = "render Panola voice(s) to an MEI music-notation document"
categories = "Notation, Utils"
related = "Classes/Panola, Classes/MSScore, Classes/PanolaMeterSplitter, Classes/PanolaMeter"
description = '''
Pure transform from Panola voice(s) + score preferences (a strong::changes:: list of meter/key settings,
clef per staff, brace grouping) to an MEI document (a String), usable by any MEI renderer (Verovio, ...). Panola itself has
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
A per-note teletype::@hairpin^cresc^:: (or teletype::dim::) ... teletype::@hairpin^end^:: span becomes a crescendo/decrescendo strong::hairpin::; teletype::@hairpin^endcresc^:: / teletype::@hairpin^enddim^:: close the open hairpin and open the opposite one at that note (messa di voce). One hairpin at a time, like slurs.

Forced breaks: pass teletype::pageBreaks:: / teletype::systemBreaks:: (Arrays of 1-based measure numbers) to start a new strong::page:: (teletype::<pb/>::) or strong::system:: (teletype::<sb/>::) at those measures. Page breaks switch to manual pagination (auto page-fill off); system breaks keep auto pagination. The renderer selects the mode from the encoded breaks.

A single teletype::@art:: may strong::combine several articulations:: with teletype::+::, e.g.
teletype::@art^staccato+accent^::, rendering them together as one space-separated teletype::artic::
list (teletype::artic="acc stacc"::). Each teletype::+:: part may itself be a strong::sticky::
toggle: teletype::@art^staccato:on+accent^:: begins a staccato passage strong::and:: accents just
that note. The list is order-independent and de-duplicated.

strong::Mid-piece meter / key changes:: are driven by the teletype::changes:: argument: an Array of Events
teletype::( measure:, meter:, key: ):: applied at the strong::start:: of their (1-based) measure, each
field carrying forward to later measures; the teletype::measure: 1:: entry sets the initial meter and key
(defaulting to teletype::4/4:: and teletype::Cmajor:: when absent). A mid-piece meter or key change emits a
mid-teletype::<section>:: teletype::<scoreDef>::, and a meter change varies the bar length from that
measure on. A per-note strong::inline clef:: property teletype::@clef^bass^:: (also teletype::@clef^treble^::,
teletype::@clef^alto^::, teletype::@clef^tenor^::) switches that staff's clef at that note, mid-measure
allowed; the initial clef per staff stays the teletype::clefs:: argument.

Typical use:
code::
PanolaMEI.scoreAsMEI([Panola("c5_4 e g a"), Panola("c3_2 g2")],
    changes: [( measure: 1, meter: "4/4", key: \Cmajor )], clefs: [\treble, \bass], braces: [[1,2]]);
::
Also reachable as teletype::aPanola.asMEI(meter, key, clef):: (see link::Classes/Panola::).
'''
*/
PanolaMEI {

	/*
	[classmethod.scoreAsMEI]
	description = "render several Panola voices as one multi-staff MEI score (one voice per staff, top first), including meter-aware splitting-and-tying at metrical boundaries, per-beat beaming, tuplets (an incomplete teletype::*m/d:: run is completed music21-style — the following note or rest is split into the teletype::<tuplet>:: bracket with link::Classes/PanolaDurationSpeller::, a note tied out and a rest split into a tuplet rest; a trailing incomplete tuplet (nothing follows) or a too-short follower stays a warned partial bracket; a complete tuplet crossing a barline is split into tied per-measure teletype::<tuplet>:: brackets, a straddling member cut at the barline into tied sub-tuplet notes, falling back to the whole bracket plus a warning when a fragment is not expressible at the tuplet ratio), per-note dynamics/articulation, and slurs. An strong::additive meter:: numerator (teletype::\"2+2+3/8\"::) groups the bar so the splitting, per-group beaming, and the meter signature all follow the grouping, while a plain teletype::\"7/8\":: stays ungrouped. Score-level strong::meter/key changes:: come from the teletype::changes:: list (Events teletype::( measure:, meter:, key: ):: applied at a measure start, each field carried forward), emitting a mid-teletype::<section>:: teletype::<scoreDef>:: where meter or key changes; a per-note teletype::@clef^bass^:: switches that staff's clef inline (mid-measure allowed)."
	[classmethod.scoreAsMEI.args]
	voices = "an Array of Panola instances (one per staff, top to bottom)"
	changes = "an Array of Events ( measure:, meter:, key: ) applied at the START of their 1-based measure, each field carrying forward to later measures. The teletype::measure: 1:: entry sets the strong::initial:: meter and key (nil defaults to a single teletype::4/4:: / \\Cmajor entry). A meter String may be strong::additive:: (teletype::\"2+2+3/8\"::) to group the bar — the durations split at the group boundaries, beam per group, and print an strong::additive meter signature:: — while a plain numerator (teletype::\"7/8\"::) stays ungrouped. A mid-piece meter or key change emits a mid-teletype::<section>:: teletype::<scoreDef>::; a meter change varies the bar length from that measure. Independently, a per-note teletype::@clef^bass^:: (also teletype::@clef^treble^::, teletype::@clef^alto^::, teletype::@clef^tenor^::) switches that staff's clef inline, mid-measure allowed."
	clefs = "an Array of clef Symbols (\\treble \\bass \\alto \\tenor), one per staff giving the strong::initial:: clef (nil defaults to all \\treble); mid-piece clef changes use a per-note teletype::@clef::"
	braces = "an Array of [firstStaff, lastStaff] 1-based ranges to brace together (nil for none)"
	pageBreaks = "an Array of 1-based measure numbers where a new PAGE starts (nil for none), emitting a mid-section teletype::<pb/>::. Verovio then paginates strong::only:: at these marks (manual pagination — auto page-fill is off)."
	systemBreaks = "an Array of 1-based measure numbers where a new SYSTEM (line) starts (nil for none), emitting teletype::<sb/>::. Unlike pageBreaks, automatic pagination is kept."
	[classmethod.scoreAsMEI.returns]
	what = "an MEI document (a String)"
	*/
	*scoreAsMEI {
		| voices, changes, clefs = nil, braces = nil, pageBreaks = nil, systemBreaks = nil |

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
				var art = ev[\art] ? "", dyn = ev[\dyn] ? "", noteSet, parts;
				// one @art value may combine several articulations with '+' (e.g. "staccato+accent");
				// each part is either a sticky "name:on"/"name:off" toggle or a bare per-note name.
				parts = (art == "").if({ [] }, { art.split($+) });
				// sticky toggles change the carried-forward set; apply only when the whole art value
				// CHANGES (a static [] value carries forward, so re-applying every note would be
				// redundant, and re-applying ":off" would be wrong).
				if (art != prevArt) {
					parts.do({ |p|
						if (p.includes($:)) {
							var seg = p.split($:), code = artCode.(seg[0]);
							if (code.notNil) {
								(seg[1] == "on").if({ artSet = artSet.add(code) }, { artSet.remove(code) });
							} { ("PanolaMEI: unknown articulation '" ++ seg[0] ++ "'").warn };
						};
					});
				};
				prevArt = art;
				noteSet = artSet.copy;
				// bare names (no :on/:off) add to THIS note only
				parts.do({ |p|
					if ((p != "") and: { p.includes($:).not }) {
						var code = artCode.(p);
						if (code.notNil) { noteSet = noteSet.add(code) } { ("PanolaMEI: unknown articulation '" ++ p ++ "'").warn };
					};
				});
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
		// keyFor is a function measureNumber(1-based) -> key Symbol; each note is spelled with the current
		// measure's key (keyFor.(measures.size)), so a mid-piece key change re-spells accidentals per bar.
		var voiceToMeasures = { |events, meterFor, keyFor|
			var measures = [[]], pos = 0.0, eps = 1e-6, dynams = [], openSlur = nil, slurs = [], applySlur;
			var openHairpin = nil, hairpins = [], applyHairpin;
			var units, ui = 0, containers = [0.25, 0.5, 1.0, 2.0, 4.0];
			// variable bar lengths: bb (bar length in QL) and pmeter (the PanolaMeter for the splitter)
			// belong to the CURRENT measure. They start at measure 1 and are re-read from
			// meterFor.(measures.size) whenever a new measure begins (see refreshMeter, called after every
			// `measures = measures.add([])`). A constant meter makes meterFor return the same descriptor
			// each measure, so bb/pmeter never change and the output stays byte-identical.
			var md0 = meterFor.(1), bb = md0[\bb], pmeter = md0[\pmeter];
			var refreshMeter = { var d = meterFor.(measures.size); bb = d[\bb]; pmeter = d[\pmeter]; };
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
			// pair @hairpin^cresc/dim/end/endcresc/enddim^ into hairpin markers (form cres|dim). one open
			// hairpin at a time (like slurs); endcresc/enddim close the open one and open a new one of that
			// form at the SAME note (messa di voce). m/ts = 1-based measure and beat. warn + recover.
			applyHairpin = { |hpVal, m, ts|
				var formOf = { |v|
					case
					{ (v == "cresc") or: { v == "crescendo" } } { "cres" }
					{ (v == "dim") or: { v == "decresc" } or: { v == "decrescendo" } or: { v == "diminuendo" } } { "dim" }
					{ true } { nil };
				};
				var closeOpen = {
					if (openHairpin.notNil) {
						hairpins = hairpins.add(( startMeasure: openHairpin[\measure], startTstamp: openHairpin[\tstamp],
							endMeasure: m, endTstamp: ts, form: openHairpin[\form] ));
						openHairpin = nil;
					};
				};
				case
				{ hpVal == "end" } {
					if (openHairpin.isNil) { "PanolaMEI: hairpin end with no open hairpin; ignored".warn };
					closeOpen.value;
				}
				{ (hpVal == "endcresc") or: { hpVal == "enddim" } } {
					if (openHairpin.isNil) { "PanolaMEI: hairpin endcresc/enddim with no open hairpin; only opening a new one".warn };
					closeOpen.value;
					openHairpin = ( measure: m, tstamp: ts, form: (hpVal == "endcresc").if({ "cres" }, { "dim" }) );
				}
				{ formOf.(hpVal).notNil } {
					if (openHairpin.notNil) { "PanolaMEI: hairpin start while a hairpin is open; the previous one is dropped".warn };
					openHairpin = ( measure: m, tstamp: ts, form: formOf.(hpVal) );
				}
				{ true } { if (hpVal != "") { ("PanolaMEI: unknown hairpin value '" ++ hpVal ++ "'").warn } };
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
							if ((mev[\hairpin] ? "") != "") { applyHairpin.(mev[\hairpin], measures.size, sub + 1) };
							frecs = frecs.add(( str: clefEl.(mev[\clef]) ++ meiElement.(mev, mev[\meidur], mev[\dots], nil, keyFor.(measures.size)),
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
							if ((dev[\hairpin] ? "") != "") { applyHairpin.(dev[\hairpin], measures.size, sub + 1) };
						};
						compSp[\components].do({ |x, ci|
							var hasPrev = (ci > 0),
								hasNext = (ci < (compSp[\components].size - 1)) or: { hasRemainder },
								ctie = compRest.if({ nil }, {
									(hasPrev and: { hasNext }).if({ "m" },
										{ hasPrev.if({ "t" }, { hasNext.if({ "i" }, { nil }) }) }) }),
								compEv = compRest.if({ restEv }, { dev });
							frecs = frecs.add(( str: (ci == 0).if({ clefEl.(dev[\clef]) }, { "" }) ++ meiElement.(compEv, x[\meidur], x[\dots], ctie, keyFor.(measures.size)),
								md: x[\meidur].asInteger, rest: compRest, beatPos: sub,
								tup: ( num: unit[\num], numbase: unit[\numbase] ) ));
							sub = sub + x[\ql].asFloat;
						});
						wrapTuplets.(frecs).do({ |r| measures[measures.size-1] = measures[measures.size-1].add(r) });
						pos = pos + container;
						if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0; refreshMeter.() };
						// (iii) reduce a note/rest donor to its remainder (tied in when a note) for the next iteration
						if (canDonor) {
							if (hasRemainder) {
								units[ui + 1] = ( kind: \normal, ev: dev.copy.put(\beats, dev[\beats] - remainder).put(\tieIn, compRest.not).put(\dynMark, nil).put(\slur, "").put(\hairpin, "").put(\clef, "") );
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
							pendDyn = [], pendSlur = [], pendHairpin = [],
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
									if ((mev[\hairpin] ? "") != "") { pendHairpin = pendHairpin.add([ mev[\hairpin], measures.size + (buckets.size - 1), sub + 1 ]) };
									while { (mb > eps) and: { ok } } {
										var room = bb - sub;
										(mb <= (room + eps)).if({
											// the (remaining) member fits in this bar
											var md = firstPiece.if({ mev[\meidur] }, { var f = fragAt.(mb); f.isNil.if({ ok = false; nil }, { f[\meidur] }) }),
												dt = firstPiece.if({ mev[\dots] }, { var f = fragAt.(mb); f.isNil.if({ 0 }, { f[\dots] }) }),
												tie = firstPiece.if({ nil }, { "t" });
											if (ok) {
												buckets[buckets.size - 1] = buckets[buckets.size - 1].add(
													( str: firstPiece.if({ clefEl.(mev[\clef]) }, { "" }) ++ meiElement.(mev, md, dt, tie, keyFor.(measures.size + (buckets.size - 1))), md: md.asInteger, rest: mev[\rest], beatPos: sub, tup: ratio ));
											};
											sub = sub + mb; mb = 0;
											if ((bb - sub) < eps) { buckets = buckets.add([]); sub = 0.0 };
										}, {
											// the member straddles the barline: emit `room` beats here (tie), cross over
											var f = fragAt.(room);
											f.isNil.if({ ok = false }, {
												var tie = firstPiece.if({ "i" }, { "m" });
												buckets[buckets.size - 1] = buckets[buckets.size - 1].add(
													( str: firstPiece.if({ clefEl.(mev[\clef]) }, { "" }) ++ meiElement.(mev, f[\meidur], f[\dots], tie, keyFor.(measures.size + (buckets.size - 1))), md: f[\meidur].asInteger, rest: mev[\rest], beatPos: sub, tup: ratio ));
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
							pendHairpin.do({ |ss| applyHairpin.(ss[0], ss[1], ss[2]) });
							split.do({ |bucket, bi|
								wrapTuplets.(bucket).do({ |r| measures[measures.size - 1] = measures[measures.size - 1].add(r) });
								if (bi < (split.size - 1)) { measures = measures.add([]) };
							});
							// pos advances over the crossed barlines using the STARTING bar's bb (buildSplit
							// closed over that bb); then re-read the meter for the measure we've landed in.
							pos = (pos + tbeats) - ((split.size - 1) * bb);
							refreshMeter.();
						} {
							// non-crossing, or a fragment could not be spelled: atomic bracket (+ warn if it crosses).
							// give each tuplet member its real sub-tuplet beat offset, so dynamics/slur endpoints
							// land on the right note (a slur inside one tuplet must not collapse to a point).
							unit[\members].inject(0.0, { |macc, mev|
								var mts = pos + macc + 1;
								if (mev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: mts, mark: mev[\dynMark] )) };
								if ((mev[\slur] ? "") != "") { applySlur.(mev[\slur], measures.size, mts) };
								if ((mev[\hairpin] ? "") != "") { applyHairpin.(mev[\hairpin], measures.size, mts) };
								macc + mev[\beats];
							});
							if (crosses) {
								("PanolaMEI: tuplet crosses a barline; kept whole in bar " ++ measures.size ++ " (fragment not expressible at the tuplet ratio)").warn;
							};
							measures[measures.size-1] = measures[measures.size-1].add(
								( str: tupletMEI.(unit, keyFor.(measures.size)), md: 0, rest: false, beatPos: pos, tuplet: true ));
							pos = pos + tbeats;
							if (pos >= (bb - eps)) { measures = measures.add([]); pos = (pos - bb).max(0.0); refreshMeter.() };
						};
					} {
						var ev = unit[\ev];
						var remaining = ev[\beats], firstFrag = true;
							if (ev[\dynMark].notNil) { dynams = dynams.add(( measure: measures.size, tstamp: pos + 1, mark: ev[\dynMark] )) };
							if ((ev[\slur] ? "") != "") { applySlur.(ev[\slur], measures.size, pos + 1) };
							if ((ev[\hairpin] ? "") != "") { applyHairpin.(ev[\hairpin], measures.size, pos + 1) };
						while { remaining > eps } {
							var take = (bb - pos).min(remaining), crosses = remaining > ((bb - pos) + eps);
							var lastFrag = crosses.not, pieces = meterPieces.(pos, take, ev[\rest], pmeter), subpos = pos, frecs = [];
							pieces.do({ |pc, c|
								var isFirst = firstFrag and: { c == 0 }, isLast = lastFrag and: { c == (pieces.size - 1) },
									hasPrev = (ev[\tieIn] == true) or: { isFirst.not }, hasNext = isLast.not, tie = nil, noteStr;
								if (ev[\rest].not) {
									tie = (hasPrev and: { hasNext }).if({ "m" },
										{ hasPrev.if({ "t" }, { hasNext.if({ "i" }, { nil }) }) });
								};
								// an @clef on this note leads its FIRST fragment only (a split note's clef precedes its first piece)
								noteStr = meiElement.(ev, pc[0], pc[1], tie, keyFor.(measures.size));
								if (isFirst) { noteStr = clefEl.(ev[\clef]) ++ noteStr };
								frecs = frecs.add(( str: noteStr, md: pc[0].asInteger,
									rest: ev[\rest], beatPos: subpos, tup: pc[3] ));
								subpos = subpos + pc[2];
							});
							wrapTuplets.(frecs).do({ |r| measures[measures.size-1] = measures[measures.size-1].add(r) });
							pos = pos + take; remaining = remaining - take; firstFrag = false;
							if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0; refreshMeter.() };
						};
					};
				};
				ui = consumedDonor.if({ ui + 2 }, { ui + 1 });
			};
			if (measures[measures.size-1].size == 0) { measures = measures.copyRange(0, measures.size - 2) };
			if (openSlur.notNil) { "PanolaMEI: unclosed slur at the end of a voice; dropped".warn };
			if (openHairpin.notNil) { "PanolaMEI: unclosed hairpin at the end of a voice; dropped".warn };
			( measures: measures, dynams: dynams, slurs: slurs, hairpins: hairpins );
		};
		var clefMap = IdentityDictionary[\treble->["G","2"], \bass->["F","4"], \alto->["C","3"], \tenor->["C","4"]];
		// inline clef change: a note's non-empty @clef ("treble"/"bass"/"alto"/"tenor") yields a
		// <clef shape line/> that prefixes its emitted str (a split note's first fragment, or a tuplet
		// member). An empty @clef yields "" (a no-op, so a clef-free note is byte-identical); an unknown
		// value warns and yields "" (no clef emitted).
		var clefEl = { |clefStr|
			var s = clefStr ? "", cm = (s == "").if({ nil }, { clefMap[s.asSymbol] });
			((s != "") and: { cm.isNil }).if({ ("PanolaMEI: unknown clef '" ++ s ++ "'; ignored").warn });
			cm.isNil.if({ "" }, { "<clef shape=\"" ++ cm[0] ++ "\" line=\"" ++ cm[1] ++ "\"/>" });
		};
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
				( str: clefEl.(ev[\clef]) ++ meiElement.(ev, ev[\meidur], ev[\dots], nil, k), md: ev[\meidur], rest: ev[\rest] );
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
			var hairpinsP = panola.customPropertyPattern("hairpin", "").asStream.all;
			var clefsP = panola.customPropertyPattern("clef", "").asStream.all;
			names.collect({ |nm, i|
				var e = parseName.(nm), d = parseDur.(durs[i]);
				e[\meidur] = d[0]; e[\dots] = d[1]; e[\mult] = d[2]; e[\div] = d[3]; e[\beats] = beats[i];
				e[\dyn] = dyns[i].asString; e[\art] = arts[i].asString; e[\slur] = slurs[i].asString;
				e[\clef] = clefsP[i].asString; e[\hairpin] = hairpinsP[i].asString;
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
		perVoice = voices.collect({ |p| voiceToMeasures.(annotateExpression.(eventsOf.(p)), meterFor, keyFor) });
		nm = perVoice.collect({ |v| v[\measures].size }).maxItem;
		// pad short voices with a whole-measure rest sized to THAT measure's bar length (meterFor of the
		// 1-based number of the measure being appended).
		perVoice = perVoice.collect({ |v| while { v[\measures].size < nm } { v[\measures] = v[\measures].add(emptyRest.(meterFor.(v[\measures].size + 1)[\bb])) }; v });
		nm.do({ |i|
			// mid-section meter/key change: emit ONE <scoreDef> before this measure carrying whichever of
			// meter.count/meter.unit and key.sig actually changed from the previous measure. i is 0-based
			// (measure number = i+1); i > 0 keeps measure 1 in the top scoreDef. A constant meter+key never
			// triggers this (byte-identical). meters compare by their display numerator (count) and unit (den),
			// so 4/4 -> 2+2/4 (same bar length, different signature) still emits a new signature.
			if (i > 0) {
				var mPrev = meterFor.(i), mCur = meterFor.(i + 1);
				var kPrev = keyFor.(i), kCur = keyFor.(i + 1);
				var meterChanged = (mCur[\count] != mPrev[\count]) or: { mCur[\den] != mPrev[\den] };
				var keyChanged = kCur != kPrev;
				if (meterChanged or: { keyChanged }) {
					var attrs = "";
					if (meterChanged) { attrs = attrs ++ " meter.count=\"" ++ mCur[\count] ++ "\" meter.unit=\"" ++ mCur[\den] ++ "\"" };
					if (keyChanged) { attrs = attrs ++ " key.sig=\"" ++ keyToSig.(kCur) ++ "\"" };
					body = body ++ "<scoreDef" ++ attrs ++ "/>";
				};
			};
			if (i > 0) {
				if ((pageBreaks ? []).includes(i + 1)) { body = body ++ "<pb/>" }
				{ if ((systemBreaks ? []).includes(i + 1)) { body = body ++ "<sb/>" } };
			};
			body = body ++ "<measure n=\"" ++ (i+1) ++ "\">";
			perVoice.do({ |v, s| body = body ++ "<staff n=\"" ++ (s+1) ++ "\"><layer n=\"1\">" ++ beamMeasure.(v[\measures][i], meterFor.(i + 1)[\groupStarts]) ++ "</layer></staff>" });
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
			perVoice.do({ |v, s|
				(v[\hairpins] ? []).select({ |hp| hp[\startMeasure] == (i+1) }).do({ |hp|
					var t1 = hp[\startTstamp], t2 = hp[\endTstamp], dm = hp[\endMeasure] - hp[\startMeasure];
					var t1s = (t1.frac < 1e-6).if({ t1.asInteger.asString }, { t1.round(0.0001).asString });
					var t2s = (t2.frac < 1e-6).if({ t2.asInteger.asString }, { t2.round(0.0001).asString });
					body = body ++ "<hairpin form=\"" ++ hp[\form] ++ "\" tstamp=\"" ++ t1s ++ "\" tstamp2=\"" ++ dm ++ "m+" ++ t2s ++ "\" staff=\"" ++ (s+1) ++ "\"/>";
				});
			});
			body = body ++ "</measure>";
		});
		^("<?xml version=\"1.0\" encoding=\"UTF-8\"?><mei xmlns=\"http://www.music-encoding.org/ns/mei\" meiversion=\"4.0.0\"><music><body><mdiv><score>"
			++ "<scoreDef meter.count=\"" ++ m0[\count] ++ "\" meter.unit=\"" ++ m0[\den] ++ "\" key.sig=\"" ++ keyToSig.(k0) ++ "\">"
			++ staffGrp.(voices.size, clefs, braces) ++ "</scoreDef><section>" ++ body ++ "</section></score></mdiv></body></music></mei>");
	}
}
