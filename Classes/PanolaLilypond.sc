/*
[general]
title = "PanolaLilypond"
summary = "render Panola voice(s) to a standalone LilyPond (.ly) document"
categories = "Notation, Utils"
related = "Classes/Panola, Classes/PanolaMEI, Classes/MSScore, Classes/PanolaMeterSplitter"
description = '''
Pure transform from Panola voice(s) + score preferences to a self-contained LilyPond source (a String)
that renders standalone (teletype::lilypond score.ly::) and is also accepted by MusicScene's LilyPond
engraver. It is the LilyPond sibling of link::Classes/PanolaMEI:: and covers the same features: meter-aware
splitting-and-tying, tuplets (incl. music21-style completion and cross-barline splitting), dynamics,
articulations, slurs, hairpins, lyrics, inline teletype::@clef::, mid-piece meter/key changes, additive
meters, page/system breaks, braces and multi-staff. Pitches are absolute with teletype::\\language "english"::;
LilyPond auto-beams and auto-respells accidentals. See link::Classes/PanolaMEI:: for the Panola property syntax.
'''
*/
PanolaLilypond {

	/*
	[classmethod.pr_pitchLy]
	description = "(private) a Panola pitch (pname, accid code s/x/f/ff or nil, octave Integer) as an absolute english-language LilyPond pitch, e.g. teletype::fs''::. Octave marks: apostrophes = octave-3 (comma below)."
	[classmethod.pr_pitchLy.args]
	pname = "the diatonic pitch letter String (a..g)"
	accid = "the accidental code String \"s\"/\"x\"/\"f\"/\"ff\", or nil"
	oct = "the octave Integer (scientific: 4 = the octave of middle C)"
	[classmethod.pr_pitchLy.returns]
	what = "a LilyPond pitch String"
	*/
	*pr_pitchLy {
		| pname, accid, oct |
		var acc = case
			{ accid == "s" } { "s" } { accid == "x" } { "ss" }
			{ accid == "f" } { "f" } { accid == "ff" } { "ff" } { true } { "" };
		var k = oct - 3, marks = "";
		k.abs.do({ marks = marks ++ (k > 0).if({ "'" }, { "," }) });
		^pname.asString.toLower ++ acc ++ marks;
	}

	/*
	[classmethod.pr_durLy]
	description = "(private) a MEI/Panola duration value token (\"1\",\"2\",\"4\",\"8\",... or \"breve\"/\"long\"/\"maxima\") plus a dot count as a LilyPond duration, e.g. teletype::4.::"
	[classmethod.pr_durLy.args]
	md = "the note-value token (a String or Integer)"
	dots = "the number of augmentation dots"
	[classmethod.pr_durLy.returns]
	what = "a LilyPond duration String"
	*/
	*pr_durLy {
		| md, dots |
		var base = case
			{ md.asString == "breve" } { "\\breve" }
			{ md.asString == "long" } { "\\longa" }
			{ md.asString == "maxima" } { "\\maxima" }
			{ true } { md.asString };
		var d = ""; dots.do({ d = d ++ "." });
		^base ++ d;
	}

	/*
	[classmethod.pr_clefLy]
	description = "(private) a clef Symbol (\\treble \\bass \\alto \\tenor) as a LilyPond clef name; an unknown clef warns and yields \"treble\"."
	[classmethod.pr_clefLy.args]
	clefSym = "a clef Symbol"
	[classmethod.pr_clefLy.returns]
	what = "a LilyPond clef-name String"
	*/
	*pr_clefLy {
		| clefSym |
		var m = IdentityDictionary[\treble->"treble", \bass->"bass", \alto->"alto", \tenor->"tenor"];
		var v = m[clefSym];
		^v.isNil.if({ ("PanolaLilypond: unknown clef '" ++ clefSym ++ "'; using treble").warn; "treble" }, { v });
	}

	/*
	[classmethod.pr_keyLy]
	description = "(private) a key Symbol (e.g. \\Gmajor, \\FsharpMinor) as a LilyPond teletype::\\key:: argument, e.g. teletype::g \\major::; an unknown key warns and yields teletype::c \\major::. LilyPond auto-respells accidentals, so no per-note key logic is needed."
	[classmethod.pr_keyLy.args]
	keySym = "a key Symbol"
	[classmethod.pr_keyLy.returns]
	what = "a LilyPond key String, e.g. \"g \\\\major\""
	*/
	*pr_keyLy {
		| keySym |
		var t = IdentityDictionary[
			\cmajor->"c \\major", \aminor->"a \\minor", \gmajor->"g \\major", \eminor->"e \\minor",
			\dmajor->"d \\major", \bminor->"b \\minor", \amajor->"a \\major", \fsharpminor->"fs \\minor",
			\emajor->"e \\major", \csharpminor->"cs \\minor", \bmajor->"b \\major", \gsharpminor->"gs \\minor",
			\fmajor->"f \\major", \dminor->"d \\minor", \bflatmajor->"bf \\major", \gminor->"g \\minor",
			\eflatmajor->"ef \\major", \cminor->"c \\minor", \aflatmajor->"af \\major", \fminor->"f \\minor",
			\dflatmajor->"df \\major", \bflatminor->"bf \\minor"
		];
		var v = t[keySym.asString.toLower.asSymbol];
		^v.isNil.if({ ("PanolaLilypond: unknown key '" ++ keySym ++ "'; using C major").warn; "c \\major" }, { v });
	}

	/*
	[classmethod.pr_meterLy]
	description = "(private) a meter String as a LilyPond time-signature command. A plain numerator (teletype::\"7/8\"::) yields teletype::\\time 7/8::; an additive numerator (teletype::\"2+2+3/8\"::) yields a teletype::\\timeAbbrev:: (LilyPond >= 2.25.33) that both displays the additive signature and groups the auto-beaming."
	[classmethod.pr_meterLy.args]
	meterStr = "a meter String, e.g. \"4/4\" or \"2+2+3/8\""
	[classmethod.pr_meterLy.returns]
	what = "a LilyPond time-signature command String"
	*/
	*pr_meterLy {
		| meterStr |
		var parts = meterStr.split($/), numStr = parts[0], den = parts[1];
		^(numStr.indexOf($+).notNil).if({
			"\\timeAbbrev #'(" ++ numStr.split($+).collect({ |g| "(" ++ g ++ " " ++ den ++ ")" }).join(" ") ++ ")";
		}, {
			"\\time " ++ numStr ++ "/" ++ den;
		});
	}

	/*
	[classmethod.pr_dynLy]
	description = "(private) a dynamic mark String as a LilyPond dynamic: predefined marks (teletype::mf::, teletype::sfz::, ...) become teletype::\\mf::; a non-standard mark falls back to a teletype::\\markup \\dynamic:: text."
	[classmethod.pr_dynLy.args]
	mark = "a dynamic mark String (e.g. \"mf\", \"sffz\")"
	[classmethod.pr_dynLy.returns]
	what = "a LilyPond post-event String"
	*/
	*pr_dynLy {
		| mark |
		var known = ["ppppp","pppp","ppp","pp","p","mp","mf","f","ff","fff","ffff","fffff",
			"fp","sf","sff","sp","spp","sfz","rfz"];
		^known.includesEqual(mark).if({ "\\" ++ mark }, { "-\\markup \\dynamic \"" ++ mark ++ "\"" });
	}

	/*
	[classmethod.pr_artLy]
	description = "(private) a space-separated MEI articulation-code list as concatenated LilyPond scripts (teletype::acc stacc:: -> teletype::->-.::). LilyPond has no spiccato script, so teletype::spicc:: maps to the staccatissimo wedge."
	[classmethod.pr_artLy.args]
	articStr = "a space-separated MEI artic-code String (e.g. \"acc stacc\"), or \"\""
	[classmethod.pr_artLy.returns]
	what = "a concatenated LilyPond script String, or \"\""
	*/
	*pr_artLy {
		| articStr |
		var m = IdentityDictionary[\stacc->"-.", \stacciss->"-!", \acc->"->", \ten->"--", \marc->"-^", \spicc->"-!"];
		^(articStr == "").if({ "" }, {
			articStr.split($ ).collect({ |c| m[c.asSymbol] ? "" }).join;
		});
	}

	/*
	[classmethod.pr_lyricTok]
	description = "(private) one lyric syllable slot as a LilyPond lyricmode token: the (quoted if it contains a space/hyphen/underscore/quote or leads with a digit) syllable, plus teletype:: --:: when it continues to the next syllable of the same word. A melisma/blank note is emitted as teletype::_:: by the caller, not here."
	[classmethod.pr_lyricTok.args]
	slot = "a syllable slot Event (syl:, con:)"
	[classmethod.pr_lyricTok.returns]
	what = "a LilyPond lyricmode token String"
	*/
	*pr_lyricTok {
		| slot |
		var syl = slot[\syl], needsQuote, q, con;
		needsQuote = syl.isEmpty or: { "0123456789".includes(syl[0]) }
			or: { syl.any({ |ch| " \t\n\"-_".includes(ch) }) };
		q = needsQuote.if({ "\"" ++ syl.replace("\\", "\\\\").replace("\"", "\\\"") ++ "\"" }, { syl });
		con = (slot[\con] == "d").if({ " --" }, { "" });
		^q ++ con;
	}

	/*
	[classmethod.scoreAsLilypond]
	description = "render several Panola voices as one standalone LilyPond score (one voice per staff, top first). See link::Classes/PanolaMEI#*scoreAsMEI:: for the argument meanings; the output is a self-contained .ly String. The teletype::global:: spine repeats a bar-length teletype::skip:: (teletype::s::) per measure so LilyPond bar-checks every staff, ends with a final teletype::\\bar \"|.\"::, staff ranges named in teletype::braces:: are grouped under a teletype::\\new GrandStaff::, and any voice shorter than the longest voice is padded with whole-bar rests so every staff fills every measure."
	[classmethod.scoreAsLilypond.args]
	voices = "an Array of Panola instances (one per staff, top to bottom)"
	changes = "an Array of Events ( measure:, meter:, key: ) applied at the start of their 1-based measure; the measure:1 entry sets the initial meter/key (nil defaults to 4/4 / \Cmajor)"
	clefs = "an Array of clef Symbols (\treble \bass \alto \tenor), one per staff (nil defaults to all \treble)"
	braces = "an Array of [firstStaff, lastStaff] 1-based ranges to brace together into a GrandStaff (nil for none)"
	pageBreaks = "an Array of 1-based measure numbers where a new PAGE starts (nil for none)"
	systemBreaks = "an Array of 1-based measure numbers where a new SYSTEM starts (nil for none)"
	lyrics = "an Array parallel to voices; each entry nil, an Array of verse-line Strings, or a bare String"
	[classmethod.scoreAsLilypond.returns]
	what = "a standalone LilyPond document (a String)"
	*/
	*scoreAsLilypond {
		| voices, changes, clefs = nil, braces = nil, pageBreaks = nil, systemBreaks = nil, lyrics = nil |
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
			var afterU = s.copyRange(1, s.size - 1);
			var starIdx = afterU.indexOf($*);
			var durPart = if (starIdx.notNil) { afterU.copyRange(0, starIdx - 1) } { afterU };
			var ratioPart = if (starIdx.notNil) { afterU.copyRange(starIdx + 1, afterU.size - 1) } { "1/1" };
			var tokens = durPart.split($ );
			var value = tokens[0].asFloat.asInteger;
			var dots = tokens.size - 1;
			var ratio = ratioPart.split($/);
			[value, dots, ratio[0].asInteger, ratio[1].asInteger];
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
		var artCode = { |name|
			IdentityDictionary[
				\staccato->"stacc", \stacc->"stacc", \staccatissimo->"stacciss", \stacciss->"stacciss",
				\accent->"acc", \acc->"acc", \tenuto->"ten", \ten->"ten",
				\marcato->"marc", \marc->"marc", \spiccato->"spicc", \spicc->"spicc"
			][name.asString.asSymbol];
		};
		var annotateExpression = { |events|
			var artSet = Set[], prevArt = "", prevDyn = "";
			events.do({ |ev|
				var art = ev[\art] ? "", dyn = ev[\dyn] ? "", noteSet, parts;
				parts = (art == "").if({ [] }, { art.split($+) });
				if (art != prevArt) {
					parts.do({ |p|
						if (p.includes($:)) {
							var seg = p.split($:), code = artCode.(seg[0]);
							if (code.notNil) {
								(seg[1] == "on").if({ artSet = artSet.add(code) }, { artSet.remove(code) });
							} { ("PanolaLilypond: unknown articulation '" ++ seg[0] ++ "'").warn };
						};
					});
				};
				prevArt = art;
				noteSet = artSet.copy;
				parts.do({ |p|
					if ((p != "") and: { p.includes($:).not }) {
						var code = artCode.(p);
						if (code.notNil) { noteSet = noteSet.add(code) } { ("PanolaLilypond: unknown articulation '" ++ p ++ "'").warn };
					};
				});
				ev[\articStr] = noteSet.asArray.sort.join(" ");
				ev[\dynMark] = ((dyn != prevDyn) and: { dyn != "" }).if({ dyn }, { nil });
				prevDyn = dyn;
			});
			events;
		};
		var lyricSlotsFor = { |vi|
			var entry = (lyrics.notNil and: { vi < lyrics.size }).if({ lyrics[vi] }, { nil });
			var verseLines = case
				{ entry.isNil } { [] }
				{ entry.isString } { [ entry ] }
				{ true } { entry };
			verseLines.collect({ |ln| PanolaMEI.pr_parseLyricLine(ln) });
		};
		var attachLyrics = { |events, verseSlotLists, voiceIndex|
			var ptrs = Array.fill(verseSlotLists.size, 0);
			events.do({ |ev|
				if (ev[\rest]) { ev[\lyrics] = nil } {
					ev[\lyrics] = verseSlotLists.collect({ |slots, vi|
						var p = ptrs[vi], slot = (p < slots.size).if({ slots[p] }, { nil });
						ptrs[vi] = p + 1;
						(slot.notNil and: { slot[\melisma] != true }).if({ slot }, { nil });
					});
				};
			});
			verseSlotLists.do({ |slots, vi|
				if (ptrs[vi] < slots.size) {
					("PanolaLilypond: " ++ (slots.size - ptrs[vi]) ++ " lyric syllables past the end of voice "
						++ (voiceIndex+1) ++ " verse " ++ (vi+1) ++ " — dropped").warn;
				};
			});
			events;
		};
		var resolveChanges = { |changesArg|
			var srt = (changesArg ? [( measure: 1, meter: "4/4", key: \Cmajor )]).copy.sort({ |a, b| a[\measure] < b[\measure] });
			var cm = "4/4", ck = \Cmajor;
			srt.collect({ |c| cm = c[\meter] ? cm; ck = c[\key] ? ck; ( measure: c[\measure] ? 1, meter: cm, key: ck ) });
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
		var meterPieces = { |onsetBeats, durBeats, isRest, pmeter|
			var comps = PanolaMeterSplitter.split(
				( onsetQL: PanolaRational.fromFloat(onsetBeats),
				  durationQL: PanolaRational.fromFloat(durBeats), isRest: isRest ), pmeter);
			var out = [];
			comps.do({ |c|
				var sp = c[\spelling];
				if (sp[\inexpressible]) {
					("PanolaLilypond: inexpressible piece " ++ c[\durationQL].asString ++ " — using decompose").warn;
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
		var noteLy = { |ev, md, dt, tieOut, firstFrag = true|
			var d = PanolaLilypond.pr_durLy(md, dt), tie = (tieOut and: { ev[\rest].not }).if({ "~" }, { "" }), post = "", clefPre = "", body;
			if (firstFrag) {
				var slurV = ev[\slur] ? "", hpV = ev[\hairpin] ? "";
				if ((ev[\clef] ? "") != "") { clefPre = "\\clef " ++ PanolaLilypond.pr_clefLy(ev[\clef].asSymbol) ++ " " };
				if (ev[\dynMark].notNil) { post = post ++ PanolaLilypond.pr_dynLy(ev[\dynMark]) };
				post = post ++ PanolaLilypond.pr_artLy(ev[\articStr] ? "");
				post = post ++ case
					{ slurV == "start" } { "(" }
					{ slurV == "end" } { ")" }
					{ slurV == "endstart" } { ")(" }
					{ true } { "" };
				post = post ++ case
					{ hpV == "cresc" } { "\\<" }
					{ hpV == "dim" } { "\\>" }
					{ hpV == "end" } { "\\!" }
					{ hpV == "endcresc" } { "\\!\\<" }
					{ hpV == "enddim" } { "\\!\\>" }
					{ true } { "" };
			};
			body = if (ev[\rest]) { "r" ++ d ++ tie ++ post } {
				if (ev[\pnames].size == 1) {
					PanolaLilypond.pr_pitchLy(ev[\pnames][0], ev[\accids][0], ev[\octs][0]) ++ d ++ tie ++ post
				} {
					"<" ++ ev[\pnames].collect({ |pn, c| PanolaLilypond.pr_pitchLy(pn, ev[\accids][c], ev[\octs][c]) }).join(" ")
						++ ">" ++ d ++ tie ++ post
				}
			};
			clefPre ++ body;
		};
		var groupEvents = { |events|
			var units = [], i = 0, eps = 1e-6, containers = [0.25, 0.5, 1.0, 2.0, 4.0];
			while { i < events.size } {
				var ev = events[i];
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
		// group consecutive fragment-records sharing a tuplet ratio into one \tuplet bracket
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
					out = out.add(( str: "\\tuplet " ++ tup[\num] ++ "/" ++ tup[\numbase] ++ " { " ++ run.collect({ |r| r[\str] }).join(" ") ++ " }", tup: nil ));
					i = j;
				} { out = out.add(rec); i = i + 1 };
			};
			out;
		};
		var voiceToMeasures = { |events, meterForFn|
			var md0 = meterForFn.(1), bb = md0[\bb], pmeter = md0[\pmeter];
			var measures = [[]], pos = 0.0, eps = 1e-6;
			var units = groupEvents.(events), ui = 0, containers = [0.25, 0.5, 1.0, 2.0, 4.0];
			var refreshMeter = { var d = meterForFn.(measures.size); bb = d[\bb]; pmeter = d[\pmeter]; };
			while { ui < units.size } {
				var unit = units[ui], consumedDonor = false, completed = false;
				if ((unit[\kind] == \tuplet) and: { unit[\complete].not }) {
					var container = containers.detect({ |cc| cc >= (unit[\beats] - eps) }),
						remainder = container.notNil.if({ container - unit[\beats] }, { 0.0 }),
						donor = ((ui + 1) < units.size).if({ units[ui + 1] }, { nil }),
						inBar = container.notNil and: { (pos + container) <= (bb + eps) },
						canDonor = inBar and: { donor.notNil } and: { donor[\kind] == \normal }
							and: { (donor[\ev][\beats] + eps) >= remainder };
					if (canDonor) {
						var toks = [], compSp = PanolaDurationSpeller.spell(PanolaRational.fromFloat(remainder)),
							dev = donor[\ev], compRest = dev[\rest], hasRemainder = (dev[\beats] - remainder) > eps,
							restEv = ( rest: true );
						unit[\members].do({ |mev| toks = toks.add(noteLy.(mev, mev[\meidur], mev[\dots], false, true)); });
						compSp[\components].do({ |x, ci|
							var hasPrev = (ci > 0), hasNext = (ci < (compSp[\components].size - 1)) or: { hasRemainder },
								ctie = compRest.if({ nil }, {
									(hasPrev and: { hasNext }).if({ "m" },
										{ hasPrev.if({ "t" }, { hasNext.if({ "i" }, { nil }) }) }) }),
								compEv = compRest.if({ restEv }, { dev }),
								tieOut = (ctie == "i") or: { ctie == "m" };
							toks = toks.add(noteLy.(compEv, x[\meidur], x[\dots], tieOut, ci == 0));
						});
						measures[measures.size-1] = measures[measures.size-1].add(
							"\\tuplet " ++ unit[\num] ++ "/" ++ unit[\numbase] ++ " { " ++ toks.join(" ") ++ " }");
						pos = pos + container;
						if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0; refreshMeter.() };
						if (hasRemainder) {
							units[ui + 1] = ( kind: \normal, ev: dev.copy.put(\beats, dev[\beats] - remainder).put(\dynMark, nil).put(\articStr, "").put(\slur, "").put(\hairpin, "").put(\clef, "") );
						} { consumedDonor = true };
						completed = true;
					} {
						("PanolaLilypond: incomplete tuplet (" ++ unit[\members].size ++ " notes, ratio "
							++ unit[\num] ++ ":" ++ unit[\numbase] ++ ") — emitting a partial bracket").warn;
					};
				};
				if (completed.not) {
					if (unit[\kind] == \tuplet) {
						var tbeats = unit[\beats],
							crosses = (tbeats > ((bb - pos) + eps)) and: { (bb - pos) > eps },
							ratio = ( num: unit[\num], numbase: unit[\numbase] ),
							buildSplit = {
								var buckets = [[]], sub = pos, ok = true, speller = PanolaDurationSpeller.new,
									fragAt = { |d|
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
									while { (mb > eps) and: { ok } } {
										var room = bb - sub;
										(mb <= (room + eps)).if({
											var md = firstPiece.if({ mev[\meidur] }, { var f = fragAt.(mb); f.isNil.if({ ok = false; nil }, { f[\meidur] }) }),
												dt = firstPiece.if({ mev[\dots] }, { var f = fragAt.(mb); f.isNil.if({ 0 }, { f[\dots] }) });
											if (ok) {
												buckets[buckets.size - 1] = buckets[buckets.size - 1].add(
													( str: noteLy.(mev, md, dt, false, firstPiece), tup: ratio ));
											};
											sub = sub + mb; mb = 0;
											if ((bb - sub) < eps) { buckets = buckets.add([]); sub = 0.0 };
										}, {
											var f = fragAt.(room);
											f.isNil.if({ ok = false }, {
												buckets[buckets.size - 1] = buckets[buckets.size - 1].add(
													( str: noteLy.(mev, f[\meidur], f[\dots], true, firstPiece), tup: ratio ));
												buckets = buckets.add([]); mb = mb - room; sub = 0.0; firstPiece = false;
											});
										});
									};
								});
								ok.if({ buckets }, { nil });
							},
							split = crosses.if({ buildSplit.value }, { nil });
						if (split.notNil) {
							split.do({ |bucket, bi|
								wrapTuplets.(bucket).do({ |r| measures[measures.size - 1] = measures[measures.size - 1].add(r[\str]) });
								if (bi < (split.size - 1)) { measures = measures.add([]) };
							});
							pos = (pos + tbeats) - ((split.size - 1) * bb);
							refreshMeter.();
						} {
							if (crosses) {
								("PanolaLilypond: tuplet crosses a barline; kept whole in bar " ++ measures.size ++ " (fragment not expressible at the tuplet ratio)").warn;
							};
							measures[measures.size-1] = measures[measures.size-1].add(
								"\\tuplet " ++ unit[\num] ++ "/" ++ unit[\numbase] ++ " { "
								++ unit[\members].collect({ |m| noteLy.(m, m[\meidur], m[\dots], false, true) }).join(" ") ++ " }");
							pos = pos + tbeats;
							if (pos >= (bb - eps)) { measures = measures.add([]); pos = (pos - bb).max(0.0); refreshMeter.() };
						};
					} {
						var ev = unit[\ev];
						var remaining = ev[\beats], firstFrag = true;
						while { remaining > eps } {
							var take = (bb - pos).min(remaining), crosses = remaining > ((bb - pos) + eps);
							var lastFrag = crosses.not, pieces = meterPieces.(pos, take, ev[\rest], pmeter), frecs = [];
							pieces.do({ |pc, c|
								var isLast = lastFrag and: { c == (pieces.size - 1) };
								var tieOut = ev[\rest].not and: { isLast.not };
								frecs = frecs.add(( str: noteLy.(ev, pc[0], pc[1], tieOut, firstFrag), tup: pc[3] ));
								firstFrag = false;
							});
							wrapTuplets.(frecs).do({ |r| measures[measures.size-1] = measures[measures.size-1].add(r[\str]) });
							pos = pos + take; remaining = remaining - take;
							if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0; refreshMeter.() };
						};
					};
				};
				ui = consumedDonor.if({ ui + 2 }, { ui + 1 });
			};
			if (measures[measures.size-1].size == 0) { measures = measures.copyRange(0, measures.size - 2) };
			measures;
		};
		// group braced staff ranges into a GrandStaff (a brace); ungrouped staves stay siblings
		var groupStaves = { |strs, br|
			var acc = "", n = 1, nst = strs.size, bb2 = br ? [];
			while { n <= nst } {
				var grp = bb2.detect({ |b| b[0] == n });
				if (grp.notNil) {
					acc = acc ++ "  \\new GrandStaff <<\n" ++ (grp[0]..grp[1]).collect({ |c| strs[c-1] }).join("\n") ++ "\n  >>\n";
					n = grp[1] + 1;
				} { acc = acc ++ strs[n-1] ++ "\n"; n = n + 1 };
			};
			acc;
		};
		var resolved, atFor, meterForFn, keyForFn, allEvents, perVoice, nm, staffStrs, spine, out;
		resolved = resolveChanges.(changes);
		atFor = { |i| var r = resolved.select({ |c| c[\measure] <= i }).last; r ? ( measure: 1, meter: "4/4", key: \Cmajor ) };
		meterForFn = { |i| parseMeter.(atFor.(i)[\meter]) };
		keyForFn = { |i| atFor.(i)[\key] };
		clefs = clefs ? voices.collect({ \treble });
		allEvents = voices.collect({ |p, vi| attachLyrics.(annotateExpression.(eventsOf.(p)), lyricSlotsFor.(vi), vi) });
		perVoice = allEvents.collect({ |evs| voiceToMeasures.(evs, meterForFn) });
		nm = perVoice.collect({ |m| m.size }).maxItem.max(1);
		perVoice = perVoice.collect({ |measures|
			while { measures.size < nm } {
				var md = meterForFn.(measures.size + 1);
				measures = measures.add([ "r1*" ++ md[\num] ++ "/" ++ md[\den] ]);
			};
			measures;
		});
		staffStrs = perVoice.collect({ |measures, vi|
			var lyricSet = (lyricSlotsFor.(vi).size > 0).if({ "\\set melismaBusyProperties = #'(tieMelismaBusy) " }, { "" });
			var lyr = "";
			// each verse's Lyrics goes right after THIS voice's staff, so it sits inside the staff group
			// (under the staff, matching Verovio) rather than below the whole group.
			lyricSlotsFor.(vi).do({ |slots, vv|
				var toks = allEvents[vi].select({ |ev| ev[\rest].not }).collect({ |ev|
					var slot = ev[\lyrics][vv];
					slot.isNil.if({ "_" }, { PanolaLilypond.pr_lyricTok(slot) });
				});
				lyr = lyr ++ "\n    \\new Lyrics \\lyricsto \"v" ++ (vi+1) ++ "\" { " ++ toks.join(" ") ++ " }";
			});
			"    \\new Staff << \\global \\new Voice = \"v" ++ (vi+1) ++ "\" { \\clef "
				++ PanolaLilypond.pr_clefLy(clefs[vi]) ++ " " ++ lyricSet ++ measures.collect({ |m| m.join(" ") }).join(" | ") ++ " } >>"
				++ lyr;
		});
		spine = "global = { ";
		nm.do({ |idx|
			var m1 = idx + 1, cur = meterForFn.(m1), curKey = keyForFn.(m1),
				prev = (m1 > 1).if({ meterForFn.(m1 - 1) }, { nil }),
				prevKey = (m1 > 1).if({ keyForFn.(m1 - 1) }, { nil }),
				meterChanged = (m1 == 1) or: { (cur[\count] != prev[\count]) or: { cur[\den] != prev[\den] } },
				keyChanged = (m1 == 1) or: { curKey != prevKey };
			if (meterChanged) { spine = spine ++ PanolaLilypond.pr_meterLy(atFor.(m1)[\meter]) ++ " " };
			if (keyChanged) { spine = spine ++ "\\key " ++ PanolaLilypond.pr_keyLy(curKey) ++ " " };
			if (m1 > 1) {
				if ((pageBreaks ? []).includes(m1)) { spine = spine ++ "\\pageBreak " }
				{ if ((systemBreaks ? []).includes(m1)) { spine = spine ++ "\\break " } };
			};
			spine = spine ++ "s1*" ++ cur[\num] ++ "/" ++ cur[\den] ++ (m1 < nm).if({ " | " }, { " " });
		});
		spine = spine ++ "\\bar \"|.\" }";
		out = "\\version \"2.25.0\"\n\\language \"english\"\n\\header { tagline = ##f }\n"
			// keep systems from clashing when several share one cropped image (crop adds no inter-system margin)
			++ "\\paper { indent = 0\\mm system-system-spacing.basic-distance = #18 system-system-spacing.padding = #6 }\n"
			++ spine ++ "\n\\score { <<\n" ++ groupStaves.(staffStrs, braces) ++ ">> }\n";
		^out;
	}
}
