Panola {
    var <parsed_notation;
    var <note_to_midi;
    var <gOCTAVE_DEFAULT;
    var <gOCTAVE_DEFAULT;
    var <gDURATION_DEFAULT;
    var <gMODIFIER_DEFAULT;
    var <gMULTIPLIER_DEFAULT;
    var <gDIVIDER_DEFAULT;
    var <gVOLUME_DEFAULT;
    var <gPLAYDUR_DEFAULT;
    var <gLAG_DEFAULT;
    var <gDOTS_DEFAULT;
    var <gTEMPO_DEFAULT;
    var <>customProperties;

    *new {
        |notation, octave_default="4", dur_default="4", modifier_default="",
        mult_default="1", div_default="1", vol_default="0.5",
        playdur_default="0.9", lag_default="0", tempo_default="80"|

        ^super.new.init(notation, octave_default, dur_default, modifier_default, mult_default, div_default, vol_default, playdur_default, lag_default, tempo_default);

    }

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

    pr_unroll_cleanup {
        |txt|
        while ({txt.find(" (").notNil}, { txt = txt.replace(" (","("); });
        while ({txt.find("( ").notNil}, { txt = txt.replace("( ","("); });
        while ({txt.find(" *").notNil}, { txt = txt.replace(" *","*"); });
        while ({txt.find("* ").notNil}, { txt = txt.replace("* ","*"); });
        ^txt;
    }

    pr_unroll_checksyntax {
        | txt |
        var c1, c2;
        var copytxt = txt.copy;
        copytxt = this.pr_unroll_cleanup(copytxt);
        c1 = (copytxt.findAll(")*").size == copytxt.findAll(")").size);
        c2 = (copytxt.findAll("(").size == copytxt.findAll(")").size);
        ^(c1 && c2)
    }

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
                            unrolldata[unrolldata.size-1].add(recorded_digits.postln.asInteger);
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
            unrolldata[unrolldata.size-1] = unrolldata[unrolldata.size-1].add(recorded_digits.postln.asInteger);
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

    pr_unroll {
        | t |
        var u;

        t = t.replace("@", "\\");

        if (this.pr_unroll_checksyntax(t)) {
            t = this.pr_unroll_cleanup(t);
            u = this.pr_unroll_find_inner(t);
            while ({u != []}, {
                var first = u[0].postln;
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

    noteToMidi {
        | note |
        ^this.note_to_midi[note];
    }

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

    numberOfNotesOrChords {
        ^parsed_notation.size;
    }

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
        // keep only volume properties + add distance between current and previous volume property spec
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
        // keep only volume properties + add distance between current and previous volume property spec
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

    volumePattern {
        ^this.pr_animatedPattern("vol", "fixed", gVOLUME_DEFAULT);
    }

    lagPattern {
        ^this.pr_animatedPattern("lag", "fixed", gLAG_DEFAULT);
    }

    pdurPattern {
        ^this.pr_animatedPattern("pdur", "fixed", gPLAYDUR_DEFAULT);
    }

    tempoPattern {
        ^(this.pr_animatedPattern("tempo", "fixed", gTEMPO_DEFAULT)/(60.0));
    }

    customPropertyPattern {
        | customstring, default=0 |
        ^(this.pr_animatedPattern(customstring, "fixed", default));
    }

    customPropertyPatternArgs {
        | customstring, default=nil |
        if (default.isNil) { default = []; };
        ^(this.pr_animatedPatternArgs(customstring, "fixed", default));
    }

    asPbind {
        | instrument=\default, include_custom_properties=true, custom_property_defaults=nil, translate_std_keys=true, include_tempo=true|
        if (custom_property_defaults.isNil) {
            custom_property_defaults = Dictionary.newFrom([
                "vol", gVOLUME_DEFAULT,
                "lag", gLAG_DEFAULT,
                "pdur", gPLAYDUR_DEFAULT,
                "tempo", gTEMPO_DEFAULT,
            ]);
        } {
            custom_property_defaults.put("vol", gVOLUME_DEFAULT);
            custom_property_defaults.put("lag", gLAG_DEFAULT);
            custom_property_defaults.put("pdur", gPLAYDUR_DEFAULT);
            custom_property_defaults.put("tempo", gTEMPO_DEFAULT);
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

    asMidiPbind {
        | midiOut, channel=0, include_custom_properties=true, custom_property_default=nil, translate_std_keys=true, include_tempo=true |
        ^Pbindf(this.asPbind(include_custom_properties:include_custom_properties,
			custom_property_defaults:custom_property_default,translate_std_keys:translate_std_keys,include_tempo:include_tempo),
        \instrument, {},
        \type, \midi,
        \midiout, midiOut,
        \chan, channel,
        \midicmd, \noteOn);
    }

    asPmono {
        | instrument=\default, include_custom_properties=true, custom_property_defaults=nil, translate_std_keys=true, include_tempo=true|
        var result = Pmono(instrument);
        result.patternpairs_(this.asPbind(instrument, include_custom_properties, custom_property_defaults, translate_std_keys, include_tempo).patternpairs);
        ^result;
    }

    asPmonoArtic {
        | instrument=\default, include_custom_properties=true, custom_property_defaults=nil, translate_std_keys=true, include_tempo=true|
        var result = PmonoArtic(instrument);
        result.patternpairs_(this.asPbind(instrument, include_custom_properties, custom_property_defaults, translate_std_keys, include_tempo).patternpairs);
        ^result;
    }
}
