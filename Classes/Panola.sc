/*
[general]
title = "Panola"
summary = "supercollider pattern notation language"
categories = "Midi Utils"
related = "Classes/Pattern, Classes/Pbind, Classes/Pbindf, Classes/Pmono, Classes/PmonoArtic, Classes/Pseq"
description = '''
Panola implements a subset of the midi specification language mispel in supercollider.
Mispel is a domain specific language implemented in python 3 for expressive midi generation
See https://github.com/shimpe/expremigen

Mispel elements that are not supported in this supercollider version include:
tree::
## passing animation options (tweening) for the animated properties
## specifying multiple tracks in one string
## specifying midi control change messages (e.g. animating pitchbend over time with much finer time resolution than the note events)
::

A Panola notation string specifies a phrase of notes.
Notenames can be a, b, c, d, e, f, g. Optional note modifiers can be
# for a sharp, x for a double sharp, - for a flat and -- for a double flat.
Notenames also include an optional octave number. The following are all examples of valid notes:

a4, b-3, d, gx2, f#1, d--1

If you do not specify an octave number, the last specified one is reused.

Optionally one can append an underscore with an (inverse) duration value to a note, e.g.
a_4 is a quarter note, whereas a_8 is an eighth note. These are not to be confused with a4 and a8
which are a-notes in the fourth and eighth octave respectively.

If you do not specify a duration value, the last specified one is reused.

The duration value can optionally be extended with dots. Each dot adds half the duration to the specified duration.
E.g. a_4 lasts for 1 quarter note (=2 eighth notes), whereas a_4. lasts for 1.5 quarter notes (3 eighth notes).
Number of dots is remembered until a new duration is specified.

After the dots one can optionally specify a multiplier and a divider. These can be used to specify tuplets.
E.g. "a4_8*2/3 b c5 d c b4 " specifies 6 notes that together form two triplets of eighth notes.
Multiplier and divider are remembered until a new duration is specified.

Notes can be grouped using angular brackets to make chords. The properties of the first note in the chord are used for the complete chord. Properties attached to the second and later notes in the chord (other than octave number and note modifier) are ignored. The following makes a chord of four notes:

code:: "<c4 e4 g c5>" ::

Please see the example code at the bottom for more advanced usage.
'''
*/

Panola {
	/*
	[method.parsed_notation]
	description='''
	notes and chords together with their properties extracted from the panola string
	'''
	[method.parsed_notation.returns]
	what = "a list"
	*/
	var <parsed_notation;
	/*
	[method.note_to_midi]
	description='''
	a lookup table converting a note + octave string into a midi note number
	'''
	[method.note_to_midi.returns]
	what = "a Dictionary"
	*/
	var <note_to_midi;
	/*
	[method.gOCTAVE_DEFAULT]
	description='''
	the default octave of a note, in case no octave was ever specified in one of the previous notes
	(typically "4")
	'''
	[method.gOCTAVE_DEFAULT.returns]
	what = "a string"
	*/
	var <gOCTAVE_DEFAULT;
	/*
	[method.gDURATION_DEFAULT]
	description='''
	the default duration of a note in beats, in case no duration was ever specified in one of the previous notes (typically "4")
	'''
	[method.gDURATION_DEFAULT.returns]
	what = "a string"
	*/
	var <gDURATION_DEFAULT;
	/*
	[method.gMODIFIER_DEFAULT]
	description='''
	default modifier (sharp, flat, double sharp or double flat) of a note - typically the empty string
	'''
	[method.gMODIFIER_DEFAULT.returns]
	what = "a string"
	*/
	var <gMODIFIER_DEFAULT;
	/*
	[method.gMULTIPLIER_DEFAULT]
	description='''
	default duration multiplier of a note (typically "1")
	'''
	[method.gMULTIPLIER_DEFAULT.returns]
	what = "a string"
	*/
	var <gMULTIPLIER_DEFAULT;
	/*
	[method.gDIVIDER_DEFAULT]
	description='''
	default duration divider of a note (typically "1")
	'''
	[method.gDIVIDER_DEFAULT.returns]
	what = "a string"
	*/
	var <gDIVIDER_DEFAULT;
	/*
	[method.gVOLUME_DEFAULT]
	description='''
	default volume of a note, between 0 and 1 (typically "0.5")
	'''
	[method.gVOLUME_DEFAULT.returns]
	what = "a string"
	*/
	var <gVOLUME_DEFAULT;
	/*
	[method.gPLAYDUR_DEFAULT]
	description='''
	default playdur (indication for legato/staccato) of a note, between 0 and 1 (typically "0.9")
	'''
	[method.gPLAYDUR_DEFAULT.returns]
	what = "a string"
	*/
	var <gPLAYDUR_DEFAULT;
	/*
	[method.gLAG_DEFAULT]
	description='''
	default lag of a note (typically "0")
	'''
	[method.gLAG_DEFAULT.returns]
	what = "a string"
	*/
	var <gLAG_DEFAULT;
	/*
	[method.gDOTS_DEFAULT]
	description='''
	default number of dots after a note (typically an empty string) - like in traditional notation a dot adds half of the duration to the specified duration. Multiple dots are supported too.
	'''
	[method.gDOTS_DEFAULT.returns]
	what = "a string"
	*/
	var <gDOTS_DEFAULT;
	/*
	[method.gTEMPO_DEFAULT]
	description='''
	default tempo (typically 80 bpm) - note that tempo is a special key in that it influences the tempo of the complete system (so all other voices running in parallel are affected too). For this reason, when deriving supercollider patterns from panola strings, the inclusion of the tempo-key is made optional.
	'''
	[method.gTEMPO_DEFAULT.returns]
	what = "a string"
	*/
	var <gTEMPO_DEFAULT;
	/*
	[method.customProperties]
	description='''
	a lookup table containing all properties specified in the panola input string
	'''
	[method.customProperties.returns]
	what = "a Dictionary"
	*/
	var <>customProperties;

	/*
	[classmethod.new]
	description = "New creates a new Panola instance"
	[classmethod.new.args]
	notation = "a valid panola input string"
	octave_default = "default octave (default: 4)"
	dur_default = "default duration (default: 4)"
	modifier_default = "default modifier: sharp,flat,... (default: empty string)",
	mult_default= "default duration multiplier (default: 1)"
	div_default= "default duration dividier (default: 1)"
	vol_default= "default volume (default: 0.5)",
	playdur_default="default legato/staccato (default: 0.9)"
	lag_default= "default note lag (default: 0)"
	tempo_default= "default tempo in bpm (default: 80)"
	[classmethod.new.returns]
	what = "a new Panola instance"
	*/
	*new {
		|notation, octave_default="4", dur_default="4", modifier_default="",
		mult_default="1", div_default="1", vol_default="0.5",
		playdur_default="0.9", lag_default="0", tempo_default="80"|

		^super.new.init(notation, octave_default, dur_default, modifier_default, mult_default, div_default, vol_default, playdur_default, lag_default, tempo_default);

	}

	/*
	[method.init]
	description = "initializes a new Panola instance"
	[method.init.args]
	notation = "a valid panola input string"
	octave_default = "default octave (default: 4)"
	dur_default = "default duration (default: 4)"
	modifier_default = "default modifier: sharp,flat,... (default: empty string)",
	mult_default= "default duration multiplier (default: 1)"
	div_default= "default duration dividier (default: 1)"
	vol_default= "default volume (default: 0.5)",
	playdur_default="default legato/staccato (default: 0.9)"
	lag_default= "default note lag (default: 0)"
	tempo_default= "default tempo in bpm (default: 80)"
	[method.init.returns]
	what = "a new Panola instance"
	*/
	init {
		| notation, octave_default, dur_default, modifier_default,
		mult_default, div_default, vol_default, playdur_default,
		lag_default, tempo_default |
		this.customProperties = Dictionary.newFrom([
			// property name, pBind key
			"vol", \amp,
			"tempo", \tempo,
			"lag", \lag,
			"pdur", \legato,
		]);
		this.init_notation(notation, octave_default, dur_default, modifier_default,
			mult_default, div_default, vol_default, playdur_default, lag_default, tempo_default);
		this.init_midilookup();
	}

	/*
	[method.pr_unroll_cleanup]
	description = "one of a few internal functions to cleanup the panola input string before starting parsing; this one will clean up whitespaces around brackets, part of the repetitiion syntax."
	[method.pr_unroll_cleanup.args]
	txt = "a valid panola input string"
	[method.pr_unroll_cleanup.returns]
	what = "a cleaner valid panola input string"
	*/
	pr_unroll_cleanup {
		|txt|
		while ({txt.find(" (").notNil}, { txt = txt.replace(" (","("); });
		while ({txt.find("( ").notNil}, { txt = txt.replace("( ","("); });
		while ({txt.find(" *").notNil}, { txt = txt.replace(" *","*"); });
		while ({txt.find("* ").notNil}, { txt = txt.replace("* ","*"); });
		^txt;
	}
	/*
	[method.pr_unroll_checksyntax]
	description = "internal function to check if there are equally many opening brackets as closing brackets"
	[method.pr_unroll_checksyntax.args]
	txt = "a valid panola input string"
	[method.pr_unroll_checksyntax.returns]
	what = "a boolean"
	*/
	pr_unroll_checksyntax {
		| txt |
		var c1, c2;
		var copytxt = txt.copy;
		copytxt = this.pr_unroll_cleanup(copytxt);
		c1 = (copytxt.findAll(")*").size == copytxt.findAll(")").size);
		c2 = (copytxt.findAll("(").size == copytxt.findAll(")").size);
		^(c1 && c2)
	}

	/*
	[method.pr_unroll_find_inner]
	description = "internal function to unroll sections with repeat syntax into a plain panola string"
	[method.pr_unroll_find_inner.args]
	txt = "a valid panola input string"
	[method.pr_unroll_find_inner.returns]
	what = "an valid panola input string with the inner repeated section unrolled"
	*/
	pr_unroll_find_inner {
		| txt |
		var unrolldata = [];
		var stack = [];
		var depth = 0;
		var expecting_digits = false;
		var recorded_digits = "";

		txt.do({
			| char, idx |
			if (char == $() {
				stack = stack.add([idx, depth]);
				depth = depth + 1;
			} {
				if (expecting_digits) {
					if (char == $*) {
						/* ignore */
					}
					/* else */
					{
						if ([$0, $1, $2, $3, $4, $5, $6, $7, $8, $9].includes(char)) {
							recorded_digits = recorded_digits ++ char.asString;
						} {
							unrolldata[unrolldata.size-1] =
							unrolldata[unrolldata.size-1].add(recorded_digits.asInteger);
							recorded_digits = "";
							expecting_digits = false;
						}
					};
				};
				if (char == $)) {
					var idxdepth = stack.pop;
					depth = depth - 1;
					unrolldata = unrolldata.add( [idxdepth[1], idxdepth[0], idx] );
					expecting_digits = true;
				};
			};
		});
		if (expecting_digits) {
			unrolldata[unrolldata.size-1] = unrolldata[unrolldata.size-1].add(recorded_digits.asInteger);
		};
		unrolldata = unrolldata.sort({
			| el1, el2 |
			var result = false;
			if (el1[0] > el2[0]) {
				result = true; } {
				if (el1[0] < el2[0]) {
					result = false } {
					if (el1[1] < el2[1]) {
						result = true;
					} {
						result = false;
					};
				}
			}
		});
		^unrolldata;
	}

	/*
	[method.pr_unroll]
	description = "internal function to unroll sections with repeat syntax into a plain panola string"
	[method.pr_unroll.args]
	t = "a valid panola input string"
	[method.pr_unroll.returns]
	what = "an valid panola input string with all repeated sections unrolled"
	*/
	pr_unroll {
		| t |
		var u;

		t = t.replace("@", "\\");

		if (this.pr_unroll_checksyntax(t)) {
			t = this.pr_unroll_cleanup(t);
			u = this.pr_unroll_find_inner(t);
			while ({u != []}, {
				var first = u[0];
				var temp;
				var repeater = first[3].asInteger;
				var unrolled;
				temp = t.copyRange(first[1]+1, first[2]-1);
				unrolled = repeater.collect(temp).join(" ");
				temp = t.copyRange(0, first[1]-1) ++ " " ++ unrolled ++ t.copyRange(first[2]+first[3].asString.size+2, t.size-1);
				t = this.pr_unroll_cleanup(temp);
				u = this.pr_unroll_find_inner(t);
			});
		}
		^t;
	}


	/*
	[method.pr_read_token]
	description = "internal function to return the next token in the panola string"
	[method.pr_read_token.args]
	string_so_far = "string with all previously read tokens cut off"
	token_regexp = "regexp describing next token that is expected"
	token_default= "default value in case token is not found"
	semantic_token_action = "function that can act on the extracted token"
	token_not_found_action = "function to execute if the token is not found"
	[method.pr_read_token.returns]
	what = "the extracted token, and the rest of the string that is not yet parsed"
	*/
	pr_read_token {
		| string_so_far, token_regexp, token_default="", semantic_token_action=nil, token_not_found_action=nil |
		var token, aftertoken;
		token = token_default;
		aftertoken = string_so_far;
		if (string_so_far.findRegexpAt(token_regexp,0).notNil) {
			var analysis = string_so_far.findRegexpAt(token_regexp,0);
			token = analysis[0];
			aftertoken = string_so_far.copyRange(analysis[1], string_so_far.size-1);
			if (token.notNil && semantic_token_action.notNil) {
				semantic_token_action.(token, aftertoken);
			};
			if (token.isNil && token_not_found_action.notNil) {
				token_not_found_action.(aftertoken);
			};
		};
		^[token, aftertoken];
	}

	/*
	[method.init_notation]
	description = "internal function to initialize the parsing infrastructure, lookup tables, etc"
	[method.init_notation.args]
	notation = "notation string that is passed in"
	octave_default = "default octave"
	dur_default = "default duration"
	modifier_default = "default modifier (sharp, flat, ...)"
	mult_default = "default duration multiplier"
	div_default = "default duration divider"
	vol_default = "default volume"
	playdur_default = "default playdur (legato/staccato)"
	lag_default = "default lag"
	tempo_default = "default tempo"
	[method.init_notation.returns]
	what = "after running this function, the panola string is internally unrolled and parsed"
	*/
	init_notation {
		| notation, octave_default, dur_default, modifier_default,
		mult_default, div_default, vol_default, playdur_default,
		lag_default, tempo_default |

		var noteletters;
		var accumulatechord = false;
		var accumulated = [];

		~cOCTAVE_DEFAULT = octave_default;
		~cDURATION_DEFAULT = dur_default;
		~cMODIFIER_DEFAULT = modifier_default;
		~cMULTIPLIER_DEFAULT = mult_default;
		~cDIVIDER_DEFAULT = div_default;
		~cVOLUME_DEFAULT = vol_default;
		~cPLAYDUR_DEFAULT = playdur_default;
		~cLAG_DEFAULT = lag_default;
		~cTEMPO_DEFAULT = tempo_default;
		~cDOTS_DEFAULT = 0;

		gOCTAVE_DEFAULT = ~cOCTAVE_DEFAULT;
		gDURATION_DEFAULT = ~cDURATION_DEFAULT;
		gMODIFIER_DEFAULT = ~cMODIFIER_DEFAULT;
		gMULTIPLIER_DEFAULT = ~cMULTIPLIER_DEFAULT;
		gDIVIDER_DEFAULT = ~cDIVIDER_DEFAULT;
		gVOLUME_DEFAULT = ~cVOLUME_DEFAULT;
		gPLAYDUR_DEFAULT = ~cPLAYDUR_DEFAULT;
		gLAG_DEFAULT = ~cLAG_DEFAULT;
		gTEMPO_DEFAULT = ~cTEMPO_DEFAULT;
		gDOTS_DEFAULT = ~cDOTS_DEFAULT;

		if (notation[notation.size-1] != $ ) {
			notation = notation++" ";
		};
		noteletters = [];
		notation = this.unroll_loops(notation);
		notation = notation.replace("<", " < ").replace(">", " > ");
		while ({notation.find(", ").notNil}, {
			notation = notation.replace(", ", ",");
		});
		while ({notation.find(" ,").notNil}, {
			notation = notation.replace(" ,", ",");
		});

		notation = this.pr_unroll(notation);

		notation.findRegexp("[^\\ ]+(\\ )+").do({
			|x|
			if (x[1].stripWhiteSpace.compare("") != 0) {
				noteletters = noteletters.add(x[1]);
			};
		});
		noteletters.do({
			|note|
			var modifier = ~cMODIFIER_DEFAULT;
			var octave = ~cOCTAVE_DEFAULT;
			var duration = ~cDURATION_DEFAULT;
			var volume = ~cVOLUME_DEFAULT;
			var playdur = ~cPLAYDUR_DEFAULT;
			var lag = ~cLAG_DEFAULT;
			var fullnote = "";
			var letter = note[0];
			var afterletter = note.copyRange(1, note.size-1);
			var modifierregexp = "(#|x|--|-)";
			var octaveregexp = "(\\d+)";
			var durationregexp = "(\\d+)";
			var durationextensionregexp = "(\\.)+";
			var multiplierregexp = "\\*(\\d+)";
			var dividerregexp = "/(\\d+)";
			var propertyregex = "([^{\\[\\\\]+)";
			var propertytyperegex = "({|\\[)";
			var propertyvalueregex = "([^}\\]]+)";
			var aftermodifier = "";
			var afteroctave = "";
			var afterduration = "";
			var afterdurationextension = "";
			var aftermultiplier = "";
			var afterdivider = "";
			var afterproperty = "";
			var extractedproperty = "";
			var propertytype = "";
			var afterextractedproperty="";
			var propertyvalue = "";
			var props = [];
			~multiplier = ~cMULTIPLIER_DEFAULT;
			~divider = ~cDIVIDER_DEFAULT;
			~num_of_dots = ~cDOTS_DEFAULT;

			note = note.stripWhiteSpace;

			if (note.compare("<") == 0) {
				accumulated = [];
				accumulatechord = true;
			} {
				if (note.compare(">") == 0) {
					accumulatechord = false;
					parsed_notation = parsed_notation.add(accumulated);
				} {
					var parseresult;
					parseresult = this.pr_read_token(afterletter, modifierregexp, ~cMODIFIER_DEFAULT);
					modifier = parseresult[0];
					aftermodifier = parseresult[1];

					parseresult = this.pr_read_token(aftermodifier, octaveregexp, ~cOCTAVE_DEFAULT, { |token, rest| ~cOCTAVE_DEFAULT = token; });
					octave = parseresult[0];
					afteroctave = parseresult[1];
					if (afteroctave[0].notNil && afteroctave[0] == $_) {
						afteroctave = afteroctave.copyRange(1, afteroctave.size-1);
					};

					parseresult = this.pr_read_token(afteroctave, durationregexp, ~cDURATION_DEFAULT, { | token, rest |
						~cDURATION_DEFAULT = token; // update duration default, reset multiplier, divider, num_of_dots
						~cMULTIPLIER_DEFAULT = "1";
						~divider = "1";
						~cDIVIDER_DEFAULT = "1";
						~multiplier = "1";
						~cDOTS_DEFAULT = 0;
						~num_of_dots = 0;
					});
					duration = parseresult[0];
					afterduration = parseresult[1];

					parseresult = this.pr_read_token(afterduration, durationextensionregexp, ~cDOTS_DEFAULT, { | token, rest |
						~num_of_dots = token.size;
						~cDOTS_DEFAULT = ~num_of_dots; // update dots default
					});
					afterdurationextension = parseresult[1];

					parseresult = this.pr_read_token(afterdurationextension, multiplierregexp, ~cMULTIPLIER_DEFAULT, { | token, rest |
						~multiplier = token.copyRange(1, token.size-1);
						~cMULTIPLIER_DEFAULT = ~multiplier;
					});
					aftermultiplier = parseresult[1];

					parseresult = this.pr_read_token(aftermultiplier, dividerregexp, ~cDIVIDER_DEFAULT, { | token, rest |
						~divider = token.copyRange(1, token.size-1);
						~cDIVIDER_DEFAULT = ~divider;
					});
					afterdivider = parseresult[1];

					afterproperty = afterdivider;

					while({afterproperty[0] == $\\}, {
						var val = "0.5";
						var prop = "vol";

						~type = "fixed";

						afterproperty = afterproperty.copyRange(1, afterproperty.size-1);
						parseresult = this.pr_read_token(afterproperty, propertyregex, "", { | token, rest |
							var parseresult;
							var val_args;
							prop = token;
							parseresult = this.pr_read_token(rest, propertytyperegex, "[", { | token ,rest |
								if (token[0].asString.compare("{") == 0) {
									~type = "anim";
								} {
									~type = "fixed";
								};
							});
							afterextractedproperty = parseresult[1];
							val = afterextractedproperty.findRegexpAt(propertyvalueregex, 0);
							val_args = val[0].split($,);
							props = props.add([prop, ~type, val_args[0], val_args.drop(1)]);
							this.customProperties.put(prop, prop.asSymbol);
							afterproperty = afterextractedproperty.copyRange(val[1]+1, afterextractedproperty.size-1);
						});

					});

					fullnote = if (letter == $r) {letter} {letter++modifier++octave};

					if (accumulatechord) {
						accumulated = accumulated.add([fullnote, duration, ~num_of_dots, ~multiplier, ~divider, props]);
					} {
						parsed_notation = parsed_notation.add([fullnote, duration, ~num_of_dots, ~multiplier, ~divider, props]);
					};
				};
			};
		});

	}

	/*
	[method.unroll_loops]
	description = "internal function to clean up a panola input string; this function has a misleading name, it currently only removes some spaces and in fact seems to mostly duplicate pr_unroll_cleanup (TODO - investigate)"
	[method.unroll_loops.args]
	notation = "panola input string to be cleaned up"
	[method.unroll_loops.returns]
	what = "a cleaned up string"
	*/
	unroll_loops {
		| notation |
		while ({notation.find(") ").notNil}, {
			notation = notation.replace(") ", ")");
		});
		while ({notation.find(" *").notNil}, {
			notation = notation.replace(" *", "*");
		});
		while ({notation.find("* ").notNil}, {
			notation = notation.replace("* ", "*");
		});
		^notation
	}

	/*
	[method.init_midilookup]
	description = "internal function to initialize the note_to_midi lookup table"
	[method.init_midilookup.returns]
	what = "a Dictionary from notename to midinote number (and an extra entry "r" for a rest)"
	*/
	init_midilookup {
		var notenum = 0;
		var corner_case_octave_lower;
		var corner_case_octave_higher;
		var chromatic_scale;
		note_to_midi = Dictionary.new;
		chromatic_scale = [["c", "b#", "d--"],  // one row contains all synonyms (i.e. synonym for our purpose)
			["c#", "bx", "d-"],
			["d", "cx", "e--"],
			["d#", "e-", "f--"],
			["e", "dx", "f-"],
			["f", "e#", "g--"],
			["f#", "ex", "g-"],
			["g", "fx", "a--"],
			["g#", "a-"],
			["a", "gx", "b--"],
			["a#", "b-", "c--"],
			["b", "ax", "c-"]];
		corner_case_octave_lower = Set["b#", "bx"];
		corner_case_octave_higher = Set["c-", "c--"];

		11.do({
			| octave |
			chromatic_scale.do({
				| synonyms |
				synonyms.do({
					| note |
					var o = octave - 1;
					if (corner_case_octave_lower.includes(note),{
						o = o - 1;
					}, {
						if (corner_case_octave_higher.includes(note), {
							o = o + 1
						})
					});
					note_to_midi[note++o.asString] = notenum;
				});
				notenum = notenum+1;
			});
		});

		note_to_midi["r"] = Rest();
	}

	/*
	[method.noteToMidi]
	description = "look up a midi note number for a midi note name"
	[method.noteToMidi.args]
	note = "midi note name"
	[method.noteToMidi.returns]
	what = "an integer"
	*/
	noteToMidi {
		| note |
		^this.note_to_midi[note];
	}

	/*
	[method.notationnotePattern]
	description = "extracts from the current panola string a Pseq pattern containing only the note names"
	[method.notationnotePattern.returns]
	what = "a pattern (Pseq)"
	*/
	notationnotePattern {
		var notelist = parsed_notation.collect({
			| el |
			if (el[0].class == Array) { // chord
				"< "++el.collect({
					|note|
					note[0];
				}).join(" ")++" >";
			} {
				el[0];
			};
		});
		^Pseq(notelist, 1);
	}

	/*
	[method.getNoOfEvents]
	description = "extracts from the current panola string the number of events (notes) present in the pattern"
	[method.getNoOfEvents.returns]
	what = "an integer"
	*/
	getNoOfEvents {
		var notelist = parsed_notation.collect({
			| el |
			if (el[0].class == Array) { // chord
				"< "++el.collect({
					|note|
					note[0];
				}).join(" ")++" >";
			} {
				el[0];
			};
		});
		^notelist.size
	}

	/*
	[method.midinotePattern]
	description = "extracts from the current panola string a Pseq pattern containing only the midi note numbers corresponding to the notes in the panola string"
	[method.midinotePattern.returns]
	what = "a pattern (Pseq)"
	*/
	midinotePattern {
		var notelist = parsed_notation.collect({
			| el |
			if (el[0].class == Array) {
				el.collect({
					| note |
					note_to_midi[note[0].asString]
				});
			} {
				note_to_midi[el[0].asString];
			}
		});
		^Pseq(notelist, 1);
	}

	/*
	[method.notationdurationPattern]
	description = "extracts from the current panola string a Pseq pattern containing only the midi note durations in the form of a string corresponding to the notes in the panola string"
	[method.notationdurationPattern.returns]
	what = "a pattern (Pseq)"
	*/
	notationdurationPattern {
		var durlist = parsed_notation.collect({
			| el |
			var dur_el = if (el[0].class == Array) { el[0]; } { el; }; // for chords use first note properties for all chord
			var duration = dur_el[1].stripWhiteSpace;
			var num_of_dots = dur_el[2];
			var multiplier = dur_el[3];
			var divider = dur_el[4];
			var dots = "";
			var str = "";
			num_of_dots.asInteger.do({
				dots = dots + ".";
			});
			str = "(1/"++duration++dots++")*("++multiplier++"/"++divider++")";
		});
		^Pseq(durlist, 1);
	}

	/*
	[method.durationPattern]
	description = "extracts from the current panola string a Pseq pattern containing only the midi note durations in the form of numbers corresponding to the durations in beats of the notes in the panola string"
	[method.durationPattern.returns]
	what = "a pattern (Pseq)"
	*/
	durationPattern {
		var durlist = parsed_notation.collect({
			| el |
			var dur_el = if (el[0].class == Array) { el[0]; } { el; }; // for chords use first note properties for all chord
			var duration = dur_el[1];
			var num_of_dots = dur_el[2];
			var multiplier = dur_el[3];
			var divider = dur_el[4];
			(4/duration.asFloat)*(2-(1/(2.pow(num_of_dots.asInteger))))*(multiplier.asFloat/divider.asFloat);
		});
		^Pseq(durlist, 1);
	}

	/*
	[method.totalDuration]
	description = "total duration in beats of a panola string"
	[method.totalDuration.returns]
	what = "a pattern (Pseq)"
	*/
	totalDuration {
		var durlist = parsed_notation.collect({
			| el |
			var dur_el = if (el[0].class == Array) { el[0]; } { el; }; // for chords use first note properties for all chord
			var duration = dur_el[1];
			var num_of_dots = dur_el[2];
			var multiplier = dur_el[3];
			var divider = dur_el[4];
			(4/duration.asFloat)*(2-(1/(2.pow(num_of_dots.asInteger))))*(multiplier.asFloat/divider.asFloat);
		});
		^durlist.sum;
	}


	/*
	[method.numberOfNotesOrChords]
	description = "number of notes/chords in a panola string"
	[method.numberOfNotesOrChords.returns]
	what = "a pattern (Pseq)"
	*/
	numberOfNotesOrChords {
		^parsed_notation.size;
	}

	/*
	[method.pr_animatedPattern]
	description = "internal method to return a pattern generating the values of a panola property, also taking into account the defined automations - this is a generic method that is used by practically all other pattern extraction functions"
	[method.pr_animatedPattern.returns]
	what = "a pattern (Pseq)"
	*/
	pr_animatedPattern {
		| prop_name="vol", default_type = "fixed", default_propval = 0.5 |
		var currval = default_propval;
		var currargs = [];
		var patlist = [];
		// extract only properties
		var proplist = parsed_notation.collect({
			| el |
			if (el[0].class == Array) {
				el[0][5]; // properties of first note are used for all the chord
			} {
				el[5];
			};
		});
		// keep only properties + add distance between current and previous volume property spec
		var volprops = [];
		var distance = 0;
		var clumped = [];
		var clumpedsize = 0;
		proplist.do({
			|propsfornote|
			var foundVol = false;
			propsfornote.do({
				|singleprop|
				if (singleprop[0].compare(prop_name) == 0) {
					var copyprop = singleprop.copy();
					distance = distance + 1;
					copyprop = copyprop.add(distance);
					volprops = volprops.add(copyprop);
					currval = singleprop[2];
					currargs = singleprop[3];
					foundVol = true;
					distance = 0;
				};
			});
			if (foundVol.not) {
				distance = distance + 1;
			};
		});
		volprops = volprops.add([prop_name, "fixed", currval, currargs, distance]);
		// now turn into patterns
		clumped = volprops.slide(2, 1).clump(2);
		clumpedsize = clumped.size;
		if (clumped.size == 0) {
			patlist = patlist.add(Pseq([default_propval.asFloat], proplist.size));
		} {
			if (clumped[0][0][4].asInteger != 1) {
				patlist = patlist.add(Pseq([default_propval.asFloat], clumped[0][0][4].asInteger-1));
			};
			clumped.do({
				| pair, idx |
				var type = pair[0][1];
				var beginval = pair[0][2].asFloat;
				var endval = pair[1][2].asFloat;
				var args = pair[1][3];
				var length = pair[1][4].asInteger;
				var number = length;
				if (idx == (clumped.size-1)) {
					number = number + 1;
				};
				if (type.compare("anim") == 0) {
					patlist = patlist.add(Pseries(beginval, ((endval - beginval)/(length)), number));
				} {
					patlist = patlist.add(Pseq([beginval], number));
				};
			});
		}

		^Pseq(patlist, 1);
	}

	/*
	[method.pr_animatedPatternArgs]
	description = "internal generic method to return a pattern generating the argument values to a panola property, taking into account the defined automations"
	[method.pr_animatedPatternArgs.args]
	prop_name= "property name for which to generate a pattern (default: vol)"
	default_type = "default animation type (default: fixed, one of fixed/anim)"
	default_propval = "default value of a property that was not yet encountered (default: 0.5)"
	[method.pr_animatedPatternArgs.returns]
	what = "a pattern (Pseq) generating the property argument values"
	*/
	pr_animatedPatternArgs {
		| prop_name="vol", default_type = "fixed", default_propval = 0.5 |
		var currval = default_propval;
		var currargs = [];
		var patlist = [];
		// extract only properties
		var proplist = parsed_notation.collect({
			| el |
			if (el[0].class == Array) {
				el[0][5]; // properties of first note are used for all the chord
			} {
				el[5];
			};
		});
		// keep only properties + add distance between current and previous volume property spec
		var volprops = [];
		var distance = 0;
		var clumped = [];
		var clumpedsize = 0;
		proplist.do({
			|propsfornote|
			var foundVol = false;
			propsfornote.do({
				|singleprop|
				if (singleprop[0].compare(prop_name) == 0) {
					var copyprop = singleprop.copy();
					distance = distance + 1;
					copyprop = copyprop.add(distance);
					volprops = volprops.add(copyprop);
					currval = singleprop[2];
					currargs = singleprop[3];
					foundVol = true;
					distance = 0;
				};
			});
			if (foundVol.not) {
				distance = distance + 1;
			};
		});
		volprops = volprops.add([prop_name, "fixed", currval, currargs, distance]);
		// now turn into patterns
		clumped = volprops.slide(2, 1).clump(2);
		clumpedsize = clumped.size;
		if (clumped.size == 0) {
			patlist = patlist.add(Pseq([default_propval.asFloat], proplist.size));
		} {
			if (clumped[0][0][4].asInteger != 1) {
				patlist = patlist.add(Pseq([default_propval.asFloat], clumped[0][0][4].asInteger-1));
			};
			clumped.do({
				| pair, idx |
				var type = pair[0][1];
				var beginval = pair[0][2].asFloat;
				var endval = pair[1][2].asFloat;
				var args = pair[1][3];
				var length = pair[1][4].asInteger;
				var number = length;
				if (idx == (clumped.size-1)) {
					number = number + 1;
				};
				patlist = patlist.add(Pseq([args], number));
			});
		}

		^Pseq(patlist, 1);
	}

	/*
	[method.volumePattern]
	description = "method to return a pattern generating the volume values from a panola string, taking into account the defined automations"
	[method.volumePattern.returns]
	what = "a pattern (Pseq) generating the volume values"
	*/
	volumePattern {
		^this.pr_animatedPattern("vol", "fixed", gVOLUME_DEFAULT);
	}

	/*
	[method.lagPattern]
	description = "method to return a pattern generating the lag values from a panola string, taking into account the defined automations"
	[method.lagPattern.returns]
	what = "a pattern (Pseq) generating the lag values"
	*/
	lagPattern {
		^this.pr_animatedPattern("lag", "fixed", gLAG_DEFAULT);
	}

	/*
	[method.pdurPattern]
	description = "method to return a pattern generating the pdur (legato/staccato) values from a panola string, taking into account the defined automations"
	[method.pdurPattern.returns]
	what = "a pattern (Pseq) generating the pdur values"
	*/
	pdurPattern {
		^this.pr_animatedPattern("pdur", "fixed", gPLAYDUR_DEFAULT);
	}

	/*
	[method.tempoPattern]
	description = "method to return a pattern generating the tempo values from a panola string, taking into account the defined automations"
	[method.tempoPattern.returns]
	what = "a pattern (Pseq) generating the tempo values"
	*/
	tempoPattern {
		^(this.pr_animatedPattern("tempo", "fixed", gTEMPO_DEFAULT)/(60.0));
	}

	/*
	[method.customPropertyPattern]
	description = "method to return a pattern generating a user defined property's values from a panola string, taking into account the defined automations"
	[method.customPropertyPattern.args]
	customstring = "name of the property"
	default = "default value of the property if not specified explicitly"
	[method.customPropertyPattern.returns]
	what = "a pattern (Pseq) generating the customProperty values"
	*/
	customPropertyPattern {
		| customstring, default=0 |
		^(this.pr_animatedPattern(customstring, "fixed", default));
	}

    /*
	[method.customPropertyPatternArgs]
	description = "method to return a pattern generating a user defined property's argument values from a panola string, taking into account the defined automations"
	[method.customPropertyPatternArgs.args]
	customstring = "name of the property"
	default = "default value of the property if not specified explicitly"
	[method.customPropertyPatternArgs.returns]
	what = "a pattern (Pseq) generating the customProperty argument values"
	*/
	customPropertyPatternArgs {
		| customstring, default=nil |
		if (default.isNil) { default = []; };
		^(this.pr_animatedPatternArgs(customstring, "fixed", default));
	}

    /*
	[method.asPbind]
	description = "method to return a pattern generating all the properties in the panola string; intended for using with supercollider synths"
	[method.asPbind.args]
	instrument = "name of the synthdef to use in the pattern's \\instrument key"
	include_custom_properties = "boolean to indicate if the pattern should contain user defined properties as well; if set to false only properties \\instrument, \\midinote, \\dur, \\lag, \\legato, \\amp and optionally \\tempo are extracted"
	custom_property_defaults = "a Dictionary specifying default values for used defined properties"
	translate_std_keys = "a boolean to indicate that for certain standard keys like \\tempo, a transformation takes place to convert it into a number that can be passed into a tempoclock; the key \\vol is translated to \\amp in the pattern and the key \\pdur is translated into \\legato. The existence of these translations is caused by wanting to keep backward compatibility with the python expremigen library"
	include_tempo = "a boolean to indicate if tempo should be part of the Pbind. Note that the tempo key modifies the TempoClock and therefore influences all voices playing on that same TempoClock in the system (which may not be desired...)"
	[method.asPbind.returns]
	what = "a pattern (Pbind) realizing the panola string"
	*/
	asPbind {
		| instrument=\default, include_custom_properties=true, custom_property_defaults=nil, translate_std_keys=true, include_tempo=true|
		if (custom_property_defaults.isNil) {

			custom_property_defaults = Dictionary.newFrom([
				"vol", gVOLUME_DEFAULT,
				"lag", gLAG_DEFAULT,
				"pdur", gPLAYDUR_DEFAULT,
			]);
			if (include_tempo) {
				custom_property_defaults["tempo"] = gTEMPO_DEFAULT;
			};
		} {
			custom_property_defaults.put("vol", gVOLUME_DEFAULT);
			custom_property_defaults.put("lag", gLAG_DEFAULT);
			custom_property_defaults.put("pdur", gPLAYDUR_DEFAULT);
			if (include_tempo) {
				custom_property_defaults.put("tempo", gTEMPO_DEFAULT);
			}
		};
		if (include_custom_properties.not) {
			if (include_tempo) {
				^Pbind(
					\instrument, instrument,
					\midinote, this.midinotePattern,
					\dur, this.durationPattern,
					\lag, this.lagPattern,
					\legato, this.pdurPattern,
					\amp, this.volumePattern,
					\tempo, this.tempoPattern
				);
			} {
				^Pbind(
					\instrument, instrument,
					\midinote, this.midinotePattern,
					\dur, this.durationPattern,
					\lag, this.lagPattern,
					\legato, this.pdurPattern,
					\amp, this.volumePattern,
				);
			};
		} {
			var mapped_props = [];
			this.customProperties.keysValuesDo({
				|stringproperty, pbindkey|
				var default_val = 0.0;
				var scale = 1.0;
				var exclude_property = include_tempo.not.and(stringproperty.compare("tempo") == 0);
				if (exclude_property.not) {
					if (custom_property_defaults.notNil) {
						if (custom_property_defaults[stringproperty].notNil) {
							default_val = custom_property_defaults[stringproperty];
						};
					};
					if (translate_std_keys) {
						if (stringproperty.compare("tempo") == 0) {
							scale = (1/(60.0));
						};
						if (pbindkey.asString.compare("vol") == 0) {
							pbindkey = \amp;
						};
						if (pbindkey.asString.compare("pdur") == 0) {
							pbindkey = \legato;
						};
					};

					mapped_props = mapped_props.add([pbindkey, this.customPropertyPattern(stringproperty, default_val)*scale]);


				};
			});
			mapped_props = mapped_props.flatten;
			^Pbind(
				\instrument, instrument,
				\midinote, this.midinotePattern,
				\dur, this.durationPattern,
				*mapped_props
			);
		};
	}

	    /*
	[method.asMidiPbind]
	description = "method to return a pattern suitable for communication to an external synth generating all the properties in the panola string"
	[method.asMidiPbind.args]
	midiOut = "a MIDIOut instance used to communicate with a hardware synth"
	channel = "a midi channel number"
	include_custom_properties = "boolean to indicate if the pattern should contain user defined properties as well; if set to false only properties \\instrument, \\midinote, \\dur, \\lag, \\legato, \\amp and optionally \\tempo are extracted"
	custom_property_defaults = "a Dictionary specifying default values for used defined properties"
	translate_std_keys = "a boolean to indicate that for certain standard keys like \\tempo, a transformation takes place to convert it into a number that can be passed into a tempoclock; the key \\vol is translated to \\amp in the pattern and the key \\pdur is translated into \\legato. The existence of these translations is caused by wanting to keep backward compatibility with the python expremigen library"
	include_tempo = "a boolean to indicate if tempo should be part of the Pbind. Note that the tempo key modifies the TempoClock and therefore influences all voices playing on that same TempoClock in the system (which may not be desired...)"
	[method.asMidiPbind.returns]
	what = "a pattern (Pbind) realizing the panola string"
	*/
	asMidiPbind {
		| midiOut, channel=0, include_custom_properties=true, custom_property_defaults=nil, translate_std_keys=true, include_tempo=true |
		^Pbindf(this.asPbind(include_custom_properties:include_custom_properties,
			custom_property_defaults:custom_property_defaults,translate_std_keys:translate_std_keys,include_tempo:include_tempo),
		\instrument, {},
		\type, \midi,
		\midiout, midiOut,
		\chan, channel,
		\midicmd, \noteOn);
	}


    /*
	[method.asPmono]
	description = "method to return a pattern generating all the properties in the panola string; intended for using with supercollider synths"
	[method.asPmono.args]
	instrument = "name of the synthdef to use in the pattern's \\instrument key"
	include_custom_properties = "boolean to indicate if the pattern should contain user defined properties as well; if set to false only properties \\instrument, \\midinote, \\dur, \\lag, \\legato, \\amp and optionally \\tempo are extracted"
	custom_property_defaults = "a Dictionary specifying default values for used defined properties"
	translate_std_keys = "a boolean to indicate that for certain standard keys like \\tempo, a transformation takes place to convert it into a number that can be passed into a tempoclock; the key \\vol is translated to \\amp in the pattern and the key \\pdur is translated into \\legato. The existence of these translations is caused by wanting to keep backward compatibility with the python expremigen library"
	include_tempo = "a boolean to indicate if tempo should be part of the Pbind. Note that the tempo key modifies the TempoClock and therefore influences all voices playing on that same TempoClock in the system (which may not be desired...)"
	[method.asPmono.returns]
	what = "a pattern (Pmono) realizing the panola string"
	*/
	asPmono {
		| instrument=\default, include_custom_properties=true, custom_property_defaults=nil, translate_std_keys=true, include_tempo=true|
		var result = Pmono(instrument);
		result.patternpairs_(this.asPbind(instrument, include_custom_properties, custom_property_defaults, translate_std_keys, include_tempo).patternpairs);
		^result;
	}

    /*
	[method.asPmonoArtic]
	description = "method to return a pattern generating all the properties in the panola string; intended for using with supercollider synths"
	[method.asPmonoArtic.args]
	instrument = "name of the synthdef to use in the pattern's \\instrument key"
	include_custom_properties = "boolean to indicate if the pattern should contain user defined properties as well; if set to false only properties \\instrument, \\midinote, \\dur, \\lag, \\legato, \\amp and optionally \\tempo are extracted"
	custom_property_defaults = "a Dictionary specifying default values for used defined properties"
	translate_std_keys = "a boolean to indicate that for certain standard keys like \\tempo, a transformation takes place to convert it into a number that can be passed into a tempoclock; the key \\vol is translated to \\amp in the pattern and the key \\pdur is translated into \\legato. The existence of these translations is caused by wanting to keep backward compatibility with the python expremigen library"
	include_tempo = "a boolean to indicate if tempo should be part of the Pbind. Note that the tempo key modifies the TempoClock and therefore influences all voices playing on that same TempoClock in the system (which may not be desired...)"
	[method.asPmonoArtic.returns]
	what = "a pattern (Pmono) realizing the panola string"
	*/
	asPmonoArtic {
		| instrument=\default, include_custom_properties=true, custom_property_defaults=nil, translate_std_keys=true, include_tempo=true|
		var result = PmonoArtic(instrument);
		result.patternpairs_(this.asPbind(instrument, include_custom_properties, custom_property_defaults, translate_std_keys, include_tempo).patternpairs);
		^result;
	}
}

/*
[examples]
what = '''
// Panola is a way to extract Pbind keys from a concise specification.
// This makes it easier to compose "traditional" music with Pbind, with a lot less
// headache trying to keep the different keys in sync
// It's the type of system I've missed since my day one with supercollider.

// First things first. To install Panola:

Quarks.install("https://github.com/shimpe/panola");

// Now you can get the help document by typing ctrl+D with the cursor on the word
// Panola in the next line

Panola.new("a4");

// Let's start with the "Hello world" of Panola: a simple scale.
// The numbers indicate octaves.
// You don't need to repeat octave numbers if they don't change between notes.
(
~ex = Panola.new("c4 d e f g a b c5");
~player = ~ex.asPbind.play;
)

// asPbind takes a synth name as parameter (which defaults to \default).
// So the above is equivalent to
(
~ex = Panola.new("c4 d e f g a b c5");
~player = ~ex.asPbind(\default).play;
)

// instead of calling a single "asPbind" you can also extract all information separately
// like this you have optimal flexibility in what you want to use from Panola
(
~ex = Panola.new("c4 d e f g a b c5");
~pat = Pbind(\instrument, \default,	\midinote, ~ex.midinotePattern,	\dur, ~ex.durationPattern, \amp, ~ex.volumePattern,	\tempo, ~ex.tempoPattern, \lag, ~ex.lagPattern,	\legato, ~ex.pdurPattern);
~player = ~pat.play;
)

// You can make chords using angular brackets. Only note properties of the first
// note in the chord (other than octave number and note modifier (see later)) are
// taken into account.
(
~ex = Panola.new("<c4 e> <e g> <c e g c5>");
~player = ~ex.asPbind.play;
)

// You can use modifiers on the notes:
// # for sharp, x for double sharp, - for flat, -- for double flat
(
~ex = Panola.new("c4 d- e f# gx a# b-- c5");
~player = ~ex.asPbind.play;
)


// With underscores you can indicate rhythm.
// The last used rhythm value is reused until a new one is specified:
// Here's four quarter notes (_4) followed by four eighth notes (_8).
(
~ex = Panola.new("c4_4 d e f g_8 a b c5");
~player = ~ex.asPbind.play;
)

// You can use one or more dots to extend the length of the rhythm, as in traditional notation.
(
~ex = Panola.new("c4_4. d_8 e_4 f g_16 a_4.. b_4 c5");
~player = ~ex.asPbind.play;
)

// You can also use multipliers and/or dividers to change the length.
// E.g. here we use it to create a note that lasts for three eighths
// (c4_8*3) and to create tuplets (e_8*2/3 f g). Remember that last
// duration/rhythm indication is reused until a new one is specified.
(
~ex = Panola.new("c4_8*3 d_8 e_8*2/3 f g f_16 e f e g_4 b_4 c5");
~player = ~ex.asPbind.play;
)

// You can repeat certain phrases by putting them in brackets and multiply
// them with a number (corresponding to the number of repeats)( )*3
// repeats can be nested
(
~ex = Panola.new("((c4_16 d)*3 (e f)*3)*2 (g a)*3 c5_4");
~player = ~ex.asPbind.play;
)

// Now we come to the animated property system. We can attach properties to the notes and animate them over time.
// For now two types of animation are supported: linear interpolation and fixed value.
// To indicate linear interpolation, use curly brackets {}. E.g. here we let the tempo gradually increase from 80 bpm to 160 bpm:
(
~ex = Panola.new("c4@tempo{80} d e f g a b c5@tempo{160}");
~player = ~ex.asPbind.play;
)

// Different properties can be combined. Here we let the volume go up until the middle of the phrase, then let it go down again,
// while tempo is rising from 80 bpm to 160 bpm.

(
~ex = Panola.new("c4@tempo{80}@vol{0.2} d e f g@vol{0.9} a b c5@tempo{160}@vol{0.2}");
~player = ~ex.asPbind.play;
)

// If you want to use the fixed values, use square brackets instead. You can switch between fixed and animated everytime
// you specify a new property value. In the next example, tempo remains at 80 bpm until we come to note a. At that point,
// it jumps to value 100 bpm and gradually increases to 200.
(
~ex = Panola.new("c4@tempo[80] d e f g a@tempo{100} b c5 d e f g a b c6@tempo{200}");
~player = ~ex.asPbind.play;
)

// Using pdur (think: played duration), we can indicate the difference between staccato and legato.
// Here we slowly evolve from very staccato to very legato:
(
~ex = Panola.new("c4_8@pdur{0.1} d e f g a b c5 d e f g a b c6@pdur{1}");
~player = ~ex.asPbind.play;
)

// Using lag we can modulate lag. This can be a way of creating a rubato feeling.
// Linear interpolation is not ideal for this purpose, but it's better than nothing at the moment.

(
~ex = Panola.new("a5_8@tempo[120]@lag{0} b c6 a5 e d c5 d e c a4 g#4@lag{0.5} "
	"a4_8 b c5 a4 e d c4 d e c a3 g#3 a b c4 d e g# a_2@lag{0}");
~player = ~ex.asPbind.play;
)

// In addition to using predefined properties like tempo and lag, you can also use user
// defined properties, e.g. here we animate a property called "myprop".
(
~phrase = Panola.new("c d@myprop{0.1} e f g a@myprop{0.6}");
~pattern = ~phrase.customPropertyPattern("myprop"); // extract only myprop values as a pattern
~stream = ~pattern.asStream;
10.do({
	| i |
	~stream.next.postln;
});
)
// make a pbind in which the myprop appears as one of the keys, with a default value of 0 for myprop
(
~phrase = Panola.new("c d@myprop{0.1} e f g a@myprop{0.6}");
~pbind = ~phrase.asPbind(\default);
~stream = ~pbind.patternpairs[13].asStream;
10.do({
	| i |
	~stream.next.postln;
});
)
// make a pbind in which the myprop appears as one of the keys, with a customized default value of 0.4 for myprop
// (such default values are used if no values for myprop are specified yet, e.g. in the beginning of a Panola string,
//  before any myprop is defined).
(
~phrase = Panola.new("c d@myprop{0.1} e f g a@myprop{0.6}");
~pbind = ~phrase.asPbind(\default, custom_property_defaults:Dictionary.newFrom(["myprop", 0.4]));
~stream = ~pbind.patternpairs[13].asStream;
10.do({
	| i |
	~stream.next.postln;
});
)
// make pbind in which only the standard panola keys are included
(
~phrase = Panola.new("c d@myprop{0.1} e f g a@myprop{0.6}");
~pbind = ~phrase.asPbind(\default, include_custom_properties:false);
~pbind.patternpairs.postln;
)

// These custom properties can be e.g. used to drive synth arguments
// The 303 synth used below is reused from https://sccode.org/1-4Wy
// which in turn is based on code from Lance J. Putnam
(
s.waitForBoot({
	var line;

	SynthDef (\sc303 , {  arg  out=0, freq=440, wave=0, ctf=100, res=0.2,
		sus=0, dec=1.0, env=1000, gate=1, vol=0.1;
		var  filEnv, volEnv, waves;
		volEnv =  EnvGen .ar( Env .new([10e-10, 1, 1, 10e-10], [0.01, sus, dec],  'exp' ), gate, doneAction:2);
		filEnv =  EnvGen .ar( Env .new([10e-10, 1, 10e-10], [0.01, dec],  'exp' ), gate);
		waves = [ Saw .ar(freq, volEnv),  Pulse .ar(freq, 0.5, volEnv)];
		Out .ar(out,  RLPF .ar(  Select .ar(wave, waves), ctf + (filEnv * env), res).dup * vol);
	}).add;

	s.sync;

	line = Panola.new(
		"a2_16@wave[0]@vol{0.05}@tempo{120}@res{0.2}@sus{0}@env{1000}@ctf{100} a a a1 a2 a a3 a2 a a a1 a2 a3 a2 b- g@res{0.05} "
		"a2_16@wave[0] a a a1 a2 a a3@sus{0.2} a2 a@ctf{3000} a a1 a2 a3 a2 b- g@res{0.2} "
		"a2_16@wave[0] a a a1 a2 a a3 a2 a a a1 a2 a3 a2 b- g@res{0.01}@sus{0}@env{10000}@ctf{10} "
	);
	~player = line.asPbind(\sc303).play;
});

// example of automating a piano sustain pedal
// by using a custom property ped
// (the point being that property "ped" has no special meaning in panola, but we can add the meaning ourself)
// I've chosen argument values 0 - 127 to also allow sending half-pedal values for those pianos that support it
(
var midiout;
var chan = 0;
var pat = ();

if (MIDIClient.initialized.not) { MIDIClient.init; };
midiout = MIDIOut.newByName("INTEGRA-7", "INTEGRA-7 MIDI 1"); // change as needed for your digital piano

pat[\score] = Panola("c4_4@pdur[0.3]@ped[0] e g c5 c4@ped[127] e g c5 c4_4@ped[0] e g c5 ");
pat[\score_withpedalhandling] = Pbindf(
	pat[\score].asMidiPbind(midiout, chan, include_tempo:false),
	\handle, Pfunc {
		| ev |
		midiout.control(ev[\chan], 64, ev[\ped].asInteger);
});

pat[\score_withpedalhandling].play(TempoClock(120/60));
)
'''
*/