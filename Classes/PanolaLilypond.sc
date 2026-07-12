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
}
