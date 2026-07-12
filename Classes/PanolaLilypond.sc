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
}
