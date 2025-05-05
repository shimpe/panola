/*
[general]
title = "PanolaParser"
summary = "supercollider pattern notation language parser"
categories = "Midi Utils"
related = "Classes/Panola"
description = '''
PanolaParser implements the parser for panola strings. PanolaParser is implemented with the ScParco parsing library.
'''
*/

PanolaParser {
	/*
	[classmethod.noteParser]
	description="simple parser that recognizes a single-letter note name (case insensitive)"
    */
	classvar <noteParser;
	/*
	[classmethod.restParser]
	description="simple parser that recognizes a single-letter rest (case insensitive)"
    */
	classvar <restParser;
	/*
	[classmethod.noteModifier]
	description="simple parser that recognizes a note modifier (sharp, flat, double sharp, double flat)"
    */
	classvar <noteModifier;
	/*
	[classmethod.octaveParser]
	description="simple parser that recognizes an integer of at most 2 digits used to specify an octave"
    */
	classvar <octaveParser;
	/*
	[classmethod.durationParser]
	description="simple parser that recognizes a panola duration. Panola duration is an underscore, followed by a float, followed by zero or more dots followed by an optional *multiplier, followed by an optional / divisor"
    */
	classvar <durationParser;
	/*
	[classmethod.durationParser2]
	description="simple parser that recognizes a panola with only multiplier and optional divisor. "
    */
	classvar <durationParser2;
	/*
	[classmethod.durationParser3]
	description="simple parser that recognizes a panola with only divisor."
    */
	classvar <durationParser3;
	/*
	[classmethod.propertynameParser]
	description="simple parser that recognizes a panola propertyname, e.g. amp. Users can use any name they want as long as it starts with a lowercase."
    */
	classvar <propertynameParser;
	/*
	[classmethod.propertiesParser]
	description='''
simple parser that recognizes panola properties. Properties consist of a name, a type and a value. The name is described by propertynameParser. The type is determined by the kind of brackets used: [0.1] for a static property, {0.1} for an animated properties, and ^0.1^ for a one-shot property. A static property keeps its value until the next occurrence of that same property. An animated property linearly interpolates its value between successive mentionings of the property, and a one-shot property temporarily (i.e. for one note) overwrites an existing property with a new value. One-shot properties can be used to define accents e.g. without otherwise disturbing an ongoing decrescendo.
'''
    */
	classvar <propertiesParser;
	/*
	[classmethod.noteAndMod]
	description="simple parser that recognizes the combination of a notename and note modifier, e.g. a# or b--"
    */
	classvar <noteAndMod;
	/*
	[classmethod.noteAndModAndOct]
	description="simple parser that recognizes the combination of a notename, and note modifier, and an octave, e.g. e4 or f#3. Note that an octave specifier is optional. If not specified on a note, the previously specified octave is reused."
    */
	classvar <noteAndModAndOct;
	/*
	[classmethod.noteAndModAndOctAndDur]
	description='''simple parser that recognizes the combination of a notename, and note modifier, an octave and a duration. Note that an octave specifier is optional. If not specified on a note, the previously specified octave is reused. Similarly, duration is also optional, and many elements of a duration are optional as well. Durations, if specified, must start with an underscore. Then follows a float that indicates a duration, e.g. _16 for a sixteenth note. After that follows an optional number of dots (to extend the duration as in traditional notation). After that follows an optional multiplier, and after that follows an optional divisor. The multipliers and divisors make it possible to specify tuplets, e.g. a duration of _8*2/3 would make for eighth note triplets.
	'''
    */
	classvar <noteAndModAndOctAndDur;
	/*
	[classmethod.noteAndModAndOctAndDurAndProp]
	description='''simple parser that recognizes the combination of a notename, and note modifier, an octave, a duration and a list of properties. Note that an octave specifier is optional. If not specified on a note, the previously specified octave is reused. Similarly, duration is also optional, and many elements of a duration are optional as well. Durations, if specified, must start with an underscore. Then follows a float that indicates a duration, e.g. _16 for a sixteenth note. After that follows an optional number of dots (to extend the duration as in traditional notation). After that follows an optional multiplier, and after that follows an optional divisor. The multipliers and divisors make it possible to specify tuplets, e.g. a duration of _8*2/3 would make for eighth note triplets. Properties have the form @name[value] (static property) or @name{value} (animated property) or @name^value^ (one-shot property).
	'''
    */
	classvar <noteAndModAndOctAndDurAndProp;
	/*
	[classmethod.betweenChordBrackets]
	description="simple parser that recognizes things between chord brackets < >"
    */
	classvar <betweenChordBrackets;
	/*
	[classmethod.chordParser]
	description="simple parser that recognizes a chord like <a c e> or <a#_4*2/3@amp{0.5} c e-5>"
    */
	classvar <chordParser;
	/*
	[classmethod.notelistParser]
	description="simple parser that recognizes a list of notes separated by whitespace"
    */
	classvar <notelistParser;
	/*
	[classmethod.betweenRepeatBrackets]
	description="simple parser that recognizes things between repeat brackets ( ... )*2. These can be nested."
    */
	classvar <betweenRepeatBrackets;
	/*
	[classmethod.mixedNotelist]
	description="parser that parses a list of either repeatedNoteList (i.e. note list between repeat brackets) or notelist. This is actually the parser that can parse a complete panola notation string."
    */
	classvar <mixedNotelist;
	/*
	[classmethod.repeatedNotelist]
	description="parser that parses a list of notes between repeat brackets"
    */
	classvar <repeatedNotelist;

	/*
	[classmethod.new]
	description="creates a new instance of PanolaParser"
    */
	*new {
		^super.new.init;
	}

	/*
	[method.init]
	description="initializes a newly created PanolaParser"
    */
	init {
		noteParser = ScpRegexParser("[aAbBcCdDeEfFgG]").map({|result| (\type: \notename, \value: result.toLower) });
		restParser = ScpRegexParser("[rR]").map({|result| (\type: \rest) });
		noteModifier = ScpOptional(ScpChoice([
			ScpStrParser("--").map({|result| (\type : \notemodifier, \value: \doubleflat) }),
			ScpStrParser("-").map({|result| (\type: \notemodifier, \value: \flat) }),
			ScpStrParser("#").map({|result| (\type: \notemodifier, \value: \sharp) }),
			ScpStrParser("x").map({|result| (\type: \notemodifier, \value: \doublesharp) })
		])).map({|result| result ? (\type: \notemodifier, \value: \natural) }); // map missing modifier to \natural sign
		octaveParser = ScpOptional(
			ScpRegexParser("\\d\\d?").map({|result| (\type: \octave, \value: result.asInteger )})
		).map({|result| result ? (\type: \octave, \value: \previous) }); // map missing octave to \previous

		durationParser = ScpOptional(
			ScpSequenceOf([
				ScpStrParser("_"),
				ScpParserFactory.makeFloatParser.map({|result| (\type: \duration, \value: result)}),
				ScpMany(ScpStrParser(".")).map({|result| (\type: \durdots, \value: result.size)}),
				ScpOptional(ScpSequenceOf([ScpStrParser("*"), ScpParserFactory.makeIntegerParser]).map({|result| (\type: \durmultiplier, \value: result[1])})),
				ScpOptional(ScpSequenceOf([ScpStrParser("/"), ScpParserFactory.makeIntegerParser]).map({|result| (\type: \durdivider, \value: result[1])}))
		])).map({
			|result|
			if (result.isNil) {
				(\dur : \previous, \durmultiplier: \previous, \durdivider: \previous, \durdots: \previous);
			} {
				var dur = ( \dur : result[1][\value], \durdots : result[2][\value]);
				// treat divider and multiplier as one: specifying only one of the two affects the other one
				if (result[3].isNil && result[4].isNil) {
					dur[\durmultiplier] = \previous;
					dur[\durdivider] = \previous;
				} {
					if (result[3].isNil) {
						// only dividider specified ->reset multplier to 1
						dur[\durmultiplier] = 1;
						dur[\durdivider] = result[4][\value];
					} {
						if (result[4].isNil) {
							// only multiplier specified -> reset divider to 1
							dur[\durmultiplier] = result[3][\value];
							dur[\durdivider] = 1;
						} {
							// everything specified
							dur[\durmultiplier] = result[3][\value];
							dur[\durdivider] = result[4][\value];
						}
					};
				};
				dur;
			};
		});

		durationParser2 = ScpSequenceOf([
			ScpSequenceOf([ScpStrParser("*"), ScpParserFactory.makeIntegerParser]).map({|result| (\type: \durmultiplier, \value: result[1])}),
			ScpOptional(ScpSequenceOf([ScpStrParser("/"), ScpParserFactory.makeIntegerParser]).map({|result| (\type: \durdivider, \value: result[1])}))
		]).map({
			|result|
			if (result.isNil) {
				(\dur : \previous, \durmultiplier: \previous, \durdivider: \previous, \durdots: \previous);
			} {
				var dur = ( \dur : \previous, \durdots : \previous);
				// treat divider and multiplier as one: specifying only one of the two affects the other one
				if (result[0].isNil && result[1].isNil) {
					dur[\durmultiplier] = \previous;
					dur[\durdivider] = \previous;
				} {
					if (result[0].isNil) {
						// only dividider specified ->reset multplier to 1
						dur[\durmultiplier] = 1;
						dur[\durdivider] = result[1][\value];
					} {
						if (result[1].isNil) {
							// only multiplier specified -> reset divider to 1
							dur[\durmultiplier] = result[0][\value];
							dur[\durdivider] = 1;
						} {
							// everything specified
							dur[\durmultiplier] = result[0][\value];
							dur[\durdivider] = result[1][\value];
						}
					};
				};
				dur;
			};
		});

		durationParser3 = ScpSequenceOf([ScpStrParser("/"), ScpParserFactory.makeIntegerParser]).map({|result| (\type: \durdivider, \value: result[1])}).map({
			|result|
			if (result.isNil) {
				(\dur : \previous, \durmultiplier: \previous, \durdivider: \previous, \durdots: \previous);
			} {
				var dur = ( \dur : \previous, \durdots : \previous);
				if (result.isNil) {
					dur[\durmultiplier] = \previous;
					dur[\durdivider] = \previous;
				} {
					dur[\durmultiplier] = 1;
					dur[\durdivider] = result[\value];
				};
				dur;
			};
		});

		propertynameParser = ScpRegexParser("[@|\\][a-zA-z][a-zA-Z0-9]*").map({|result| (\type: \propertyname, \value: result.drop(1))});
		propertiesParser = ScpMany(
			ScpChoice([
				ScpSequenceOf([
					propertynameParser,
					ScpStrParser("{"),
					ScpParserFactory.makeFloatParser,
					ScpStrParser("}")
				]).map({|result| (\propertyname: result[0][\value], \type: \animatedproperty, \value: result[2])}),
				ScpSequenceOf([
					propertynameParser,
					ScpStrParser("["),
					ScpParserFactory.makeFloatParser,
					ScpStrParser("]")
				]).map({|result| (\propertyname: result[0][\value], \type: \staticproperty, \value: result[2])}),
				ScpSequenceOf([
					propertynameParser,
					ScpStrParser("^"),
					ScpParserFactory.makeFloatParser,
					ScpStrParser("^")
				]).map({|result| (\propertyname: result[0][\value], \type: \oneshotproperty, \value: result[2])}),
		]));

		noteAndMod = ScpChoice([
			ScpSequenceOf([noteParser, noteModifier]),
			restParser
		]);
		noteAndModAndOct = ScpChoice([
			ScpSequenceOf([noteParser, noteModifier, octaveParser]).map({
				|result|
				(\type: \note,
					\notename: result[0][\value],
					\notemodifier: result[1][\value],
					\octave: result[2][\value])
			}),
			restParser
		]);

		noteAndModAndOctAndDur = ScpSequenceOf([
			noteAndModAndOct,
			ScpChoice([durationParser3, durationParser2, durationParser]),
		]).map({|result| (\pitch : result[0], \duration: result[1] ) });

		noteAndModAndOctAndDurAndProp = ScpSequenceOf([
			noteAndModAndOctAndDur,
			propertiesParser]).map({|result| (\type: \singlenote, \info : ( \note : result[0], \props : result[1] ) ); });

		betweenChordBrackets = ScpParserFactory.makeBetween(
			ScpSequenceOf([ScpStrParser("<"), ScpParserFactory.makeWs]),
			ScpStrParser(">"));

		chordParser = betweenChordBrackets.(
			ScpManyOne(
				ScpSequenceOf([
					noteAndModAndOctAndDurAndProp,
					ScpParserFactory.makeWs
				]).map({|result| result[0] }); // remove whitespace from result
		)).map({|result| (\type: \chord, \notes : result) });

		notelistParser = ScpManyOne(ScpChoice([
			ScpSequenceOf([chordParser, ScpParserFactory.makeWs]).map({|result| result[0]}), // eat whitespace
			ScpSequenceOf([noteAndModAndOctAndDurAndProp, ScpParserFactory.makeWs]).map({|result| result[0] }) // eat whitespace
		]));

		betweenRepeatBrackets = ScpParserFactory.makeBetween(
			ScpSequenceOf([ScpStrParser("("), ScpParserFactory.makeWs]),
			ScpStrParser(")");
		);

		mixedNotelist = ScpParserFactory.forwardRef(Thunk({
			ScpManyOne(ScpChoice([repeatedNotelist, notelistParser])).map({|result| result.flatten(1); });
		}));

		repeatedNotelist = ScpSequenceOf([
			betweenRepeatBrackets.(mixedNotelist),
			ScpParserFactory.makeWs,
			ScpStrParser("*"),
			ScpParserFactory.makeWs,
			ScpParserFactory.makeIntegerParser,
			ScpParserFactory.makeWs
		]).map({
			// unroll the loop already - not sure if this is a good idea (memory consumption!)
			// but it's easier to evaluate later on
			|result|
			var parseRes = [];
			var repeat = result[4];
			repeat.do({
				parseRes = parseRes.addAll(result[0]);
			});
			parseRes.flatten(1);
		});
	}

	/*
	[method.parse]
	description="parses a panola string and returns the parse tree (or an error if parsing failed)"
    */
	parse {
		| notation, trace=false |
		^PanolaParser.mixedNotelist.run(notation, trace:trace);
	}

}