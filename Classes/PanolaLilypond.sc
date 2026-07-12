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
	description = "(private) a meter String as a LilyPond time-signature command. A plain numerator (teletype::\"7/8\"::) yields teletype::\\time 7/8::; an additive numerator (teletype::\"2+2+3/8\"::) yields a teletype::\\compoundMeter:: that both displays the additive signature and groups the auto-beaming."
	[classmethod.pr_meterLy.args]
	meterStr = "a meter String, e.g. \"4/4\" or \"2+2+3/8\""
	[classmethod.pr_meterLy.returns]
	what = "a LilyPond time-signature command String"
	*/
	*pr_meterLy {
		| meterStr |
		var parts = meterStr.split($/), numStr = parts[0], den = parts[1];
		^(numStr.indexOf($+).notNil).if({
			"\\compoundMeter #'(" ++ numStr.split($+).collect({ |g| "(" ++ g ++ " " ++ den ++ ")" }).join(" ") ++ ")";
		}, {
			"\\time " ++ numStr ++ "/" ++ den;
		});
	}

	/*
	[classmethod.scoreAsLilypond]
	description = "render several Panola voices as one standalone LilyPond score (one voice per staff, top first). See link::Classes/PanolaMEI#*scoreAsMEI:: for the argument meanings; the output is a self-contained .ly String."
	[classmethod.scoreAsLilypond.args]
	voices = "an Array of Panola instances (one per staff, top to bottom)"
	changes = "an Array of Events ( measure:, meter:, key: ) applied at the start of their 1-based measure; the measure:1 entry sets the initial meter/key (nil defaults to 4/4 / \Cmajor)"
	clefs = "an Array of clef Symbols (\treble \bass \alto \tenor), one per staff (nil defaults to all \treble)"
	braces = "an Array of [firstStaff, lastStaff] 1-based ranges to brace together (nil for none)"
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
		// a single note/chord/rest token at a written value, with an optional trailing tie
		var noteLy = { |ev, md, dt, tieOut|
			var d = PanolaLilypond.pr_durLy(md, dt), tie = tieOut.if({ "~" }, { "" });
			if (ev[\rest]) { "r" ++ d } {
				if (ev[\pnames].size == 1) {
					PanolaLilypond.pr_pitchLy(ev[\pnames][0], ev[\accids][0], ev[\octs][0]) ++ d ++ tie
				} {
					"<" ++ ev[\pnames].collect({ |pn, c| PanolaLilypond.pr_pitchLy(pn, ev[\accids][c], ev[\octs][c]) }).join(" ")
						++ ">" ++ d ++ tie
				}
			};
		};
		// ---- duration/meter helpers (verbatim from PanolaMEI) ----
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
		// ---- per voice: events -> a list of measures (each a list of tokens), split meter-aware, tied ----
		var voiceToMeasures = { |events, meterStr|
			var mdesc = parseMeter.(meterStr), bb = mdesc[\bb], pmeter = mdesc[\pmeter];
			var measures = [[]], pos = 0.0, eps = 1e-6;
			events.do({ |ev|
				var remaining = ev[\beats];
				while { remaining > eps } {
					var take = (bb - pos).min(remaining), crosses = remaining > ((bb - pos) + eps);
					var lastFrag = crosses.not, pieces = meterPieces.(pos, take, ev[\rest], pmeter);
					pieces.do({ |pc, c|
						var isLast = lastFrag and: { c == (pieces.size - 1) };
						var tieOut = ev[\rest].not and: { isLast.not };
						measures[measures.size-1] = measures[measures.size-1].add(noteLy.(ev, pc[0], pc[1], tieOut));
					});
					pos = pos + take; remaining = remaining - take;
					if ((bb - pos) < eps) { measures = measures.add([]); pos = 0.0 };
				};
			});
			if (measures[measures.size-1].size == 0) { measures = measures.copyRange(0, measures.size - 2) };
			measures;
		};
		var resolved = changes ? [( measure: 1, meter: "4/4", key: \Cmajor )];
		var meter0 = (resolved[0][\meter]) ? "4/4", key0 = (resolved[0][\key]) ? \Cmajor;
		var perVoice, staves, out;
		clefs = clefs ? voices.collect({ \treble });
		perVoice = voices.collect({ |p, vi| voiceToMeasures.(eventsOf.(p), meter0) });
		staves = perVoice.collect({ |measures, vi|
			"    \\new Staff << \\global \\new Voice = \"v" ++ (vi+1) ++ "\" { \\clef "
				++ PanolaLilypond.pr_clefLy(clefs[vi]) ++ " " ++ measures.collect({ |m| m.join(" ") }).join(" | ") ++ " } >>";
		});
		out = "\\version \"2.24.0\"\n\\language \"english\"\n\\header { tagline = ##f }\n\\paper { indent = 0\\mm }\n"
			++ "global = { " ++ PanolaLilypond.pr_meterLy(meter0) ++ " \\key " ++ PanolaLilypond.pr_keyLy(key0)
			++ " }\n\\score { <<\n" ++ staves.join("\n") ++ "\n>> }\n";
		^out;
	}
}
