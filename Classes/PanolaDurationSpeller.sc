// music21-style duration spelling: a quarterLength -> a notation spelling of one or more
// components (note value + dots + optional tuplet). See the SP1 design doc. Pure, MEI-agnostic.
/*
[general]
title = "PanolaDurationSpeller"
summary = "spell a quarterLength as conventional notation (music21-style)"
categories = "Notation, Utils"
related = "Classes/PanolaRational, Classes/Panola"
description = '''
PanolaDurationSpeller maps a single duration in strong::quarterLength:: units (teletype::1.0:: = quarter,
teletype::0.5:: = eighth, teletype::0.25:: = 16th) to a notation spelling: one or more components, each a
note value plus dots plus an optional tuplet. It tries, in order, an exact ordinary value, a dotted value,
a decomposition into several tied components, a tuplet, and (optionally) a large-tuplet fallback, otherwise
it reports the duration teletype::inexpressible::. All arithmetic is exact (link::Classes/PanolaRational::).

code::
PanolaDurationSpeller.spell(1.0);    // quarter
PanolaDurationSpeller.spell(0.75);   // dotted eighth
PanolaDurationSpeller.spell(1/3);    // eighth with a 3:2 tuplet
PanolaDurationSpeller.spell(1.25);   // quarter tied to a 16th
::

strong::Modes.:: In teletype::\exact:: mode (default) the input value is preserved exactly; if no exact
spelling exists the result is teletype::inexpressible:: (or a precise large tuplet when
teletype::allowLargeTuplets:: is set). In teletype::\quantize:: mode the value is snapped to a grid within
a tolerance before spelling; quantization is never implicit. Options (a plain Event) tune the note-type
range, dots, tuplet limits, and float handling.
'''
*/
PanolaDurationSpeller {
	/*
	[method.options]
	description = "the effective options Event (defaults merged with any overrides)"
	[method.options.returns]
	what = "an Event"
	*/
	var <options;
	classvar <noteTypes;

	/*
	[classmethod.initClass]
	description = "class initialization: build the noteTypes table (largest note value first), called once by the class library at startup"
	[classmethod.initClass.returns]
	what = "nothing (populates noteTypes)"
	*/
	*initClass {
		// name, mei dur token, [num, den] quarterLength ; largest first
		noteTypes = [
			[\duplexMaxima, nil,      [64, 1]],
			[\maxima,       "maxima", [32, 1]],
			[\longa,        "long",   [16, 1]],
			[\breve,        "breve",  [8, 1]],
			[\whole,        "1",      [4, 1]],
			[\half,         "2",      [2, 1]],
			[\quarter,      "4",      [1, 1]],
			[\eighth,       "8",      [1, 2]],
			['16th',        "16",     [1, 4]],
			['32nd',        "32",     [1, 8]],
			['64th',        "64",     [1, 16]],
			['128th',       "128",    [1, 32]],
			['256th',       "256",    [1, 64]],
			['512th',       "512",    [1, 128]],
			['1024th',      "1024",   [1, 256]],
			['2048th',      "2048",   [1, 512]]
		];
	}

	/*
	[classmethod.defaultOptions]
	description = "the default options Event (mode, grid, tolerance, maxDots, maxComponents, tuplet limits, float policy)"
	[classmethod.defaultOptions.returns]
	what = "an Event"
	*/
	*defaultOptions {
		^(mode: \exact, grid: PanolaRational(1, 512), tolerance: 1e-5, maxDots: 4,
			maxComponents: 16, maxTupletActual: 13, maxTupletNormal: 13, allowLargeTuplets: false,
			maxLargeTupletActual: 1024, maxLargeTupletNormal: 1024, minNoteType: '2048th',
			floatPolicy: \limitDenominator, maxDenominator: 65536);
	}

	/*
	[classmethod.new]
	description = "a speller whose options are defaultOptions merged with the given overrides"
	[classmethod.new.args]
	options = "an Event overriding any defaultOptions keys, or nil"
	[classmethod.new.returns]
	what = "a PanolaDurationSpeller"
	*/
	*new { | options | ^super.new.pr_init(options); }
	/*
	[method.pr_init]
	description = "initialize this speller's options: start from defaultOptions and merge in any overrides"
	[method.pr_init.args]
	opts = "an Event of option overrides, or nil"
	[method.pr_init.returns]
	what = "this PanolaDurationSpeller"
	*/
	pr_init { | opts |
		options = this.class.defaultOptions;
		opts.notNil.if({ opts.keysValuesDo({ | k, v | options[k] = v }) });
	}

	/*
	[classmethod.spell]
	description = "convenience: spell ql with a speller built from options"
	[classmethod.spell.args]
	ql = "a quarterLength"
	options = "an options Event or nil"
	[classmethod.spell.returns]
	what = "a spelling Event"
	*/
	*spell { | ql, options | ^this.new(options).spell(ql); }

	/*
	[method.pr_entry]
	description = "the noteTypes entry for a note-type name"
	[method.pr_entry.args]
	name = "a note-type Symbol"
	[method.pr_entry.returns]
	what = "an Array [name, meiToken, [num, den]] or nil"
	*/
	pr_entry { | name | ^noteTypes.detect({ | e | e[0] == name }); }
	/*
	[method.pr_qlOf]
	description = "the quarterLength of a note type as a PanolaRational"
	[method.pr_qlOf.args]
	name = "a note-type Symbol"
	[method.pr_qlOf.returns]
	what = "a PanolaRational"
	*/
	pr_qlOf { | name | var e = this.pr_entry(name); ^PanolaRational(e[2][0], e[2][1]); }
	/*
	[method.pr_meidurOf]
	description = "the MEI duration token of a note type"
	[method.pr_meidurOf.args]
	name = "a note-type Symbol"
	[method.pr_meidurOf.returns]
	what = "a String, or nil for note types with no MEI token"
	*/
	pr_meidurOf { | name | ^this.pr_entry(name)[1]; }

	/*
	[method.pr_dottedValue]
	description = "the quarterLength of a note value carrying the given number of augmentation dots"
	[method.pr_dottedValue.args]
	baseQl = "the undotted quarterLength (a PanolaRational)"
	dots = "the number of augmentation dots"
	[method.pr_dottedValue.returns]
	what = "a PanolaRational"
	*/
	pr_dottedValue { | baseQl, dots |
		var total = baseQl, half = baseQl;
		dots.do({ half = half / PanolaRational(2, 1); total = total + half });
		^total;
	}

	/*
	[method.pr_component]
	description = "build a plain (non-tuplet) component Event for a note value with dots"
	[method.pr_component.args]
	name = "a note-type Symbol"
	dots = "the number of augmentation dots"
	ql = "the component quarterLength (a PanolaRational)"
	[method.pr_component.returns]
	what = "a component Event"
	*/
	pr_component { | name, dots, ql |
		^(type: name, meidur: this.pr_meidurOf(name), dots: dots, ql: ql, tuplets: []);
	}
	/*
	[method.pr_componentTuplet]
	description = "build a component Event carrying a single actual:normal tuplet"
	[method.pr_componentTuplet.args]
	name = "a note-type Symbol"
	ql = "the component quarterLength (a PanolaRational)"
	actual = "the tuplet actual count"
	normal = "the tuplet normal count"
	[method.pr_componentTuplet.returns]
	what = "a component Event"
	*/
	pr_componentTuplet { | name, ql, actual, normal |
		^(type: name, meidur: this.pr_meidurOf(name), dots: 0, ql: ql,
			tuplets: [ (actual: actual, normal: normal, actualType: name, normalType: name) ]);
	}
	/*
	[method.pr_spelled]
	description = "build a successful spelling Event wrapping the components"
	[method.pr_spelled.args]
	ql = "the input quarterLength (a PanolaRational)"
	components = "an Array of component Events"
	[method.pr_spelled.returns]
	what = "a spelling Event (inexpressible: false)"
	*/
	pr_spelled { | ql, components | ^(inexpressible: false, ql: ql, inferred: true, components: components); }
	/*
	[method.pr_inexpressible]
	description = "build a failed spelling Event carrying a reason"
	[method.pr_inexpressible.args]
	ql = "the input quarterLength (a PanolaRational)"
	reason = "a String explaining why the duration is inexpressible"
	[method.pr_inexpressible.returns]
	what = "a spelling Event (inexpressible: true)"
	*/
	pr_inexpressible { | ql, reason | ^(inexpressible: true, ql: ql, reason: reason); }

	/*
	[method.normalizeToRational]
	description = "convert x to a PanolaRational (Floats via the floatPolicy)"
	[method.normalizeToRational.args]
	x = "a quarterLength (a PanolaRational, Integer, decimal String, or Float)"
	[method.normalizeToRational.returns]
	what = "a PanolaRational"
	*/
	normalizeToRational { | x |
		if (x.isKindOf(PanolaRational)) { ^x };
		if (x.isKindOf(String)) { ^PanolaRational.fromDecimalString(x) };
		if (x.isInteger) { ^PanolaRational(x, 1) };
		^(options[\floatPolicy] == \limitDenominator).if(
			{ PanolaRational.fromFloat(x, options[\maxDenominator]) },
			{ PanolaRational.fromFloat(x, 1.15e18) });
	}

	/*
	[method.quantizeToGrid]
	description = "snap ql to the nearest grid multiple when within tolerance (quantize mode)"
	[method.quantizeToGrid.args]
	ql = "a quarterLength (a PanolaRational)"
	[method.quantizeToGrid.returns]
	what = "a PanolaRational"
	*/
	quantizeToGrid { | ql |
		var grid = options[\grid], tol = options[\tolerance];
		var steps = (ql / grid).asFloat.round.asInteger;
		var nearest = grid * PanolaRational(steps, 1);
		^((nearest - ql).abs.asFloat <= tol).if({ nearest }, { ql });
	}

	/*
	[method.spell]
	description = "spell a quarterLength x (a PanolaRational, Integer, decimal String, or Float) as a notation spelling"
	[method.spell.args]
	x = "the quarterLength to spell"
	[method.spell.returns]
	what = "a spelling Event: on success (inexpressible: false, ql:, inferred: true, components: [ ... ]); otherwise (inexpressible: true, ql:, reason:)"
	*/
	spell { | x |
		var ql, r;
		// NOTE: SuperCollider 3.14.1's Float has no isInf/isInfinite (only isNaN), so test infinity
		// against inf / inf.neg directly (calling x.isInfinite throws doesNotUnderstand).
		if (x.isKindOf(Float) and: { x.isNaN or: { (x == inf) or: { x == inf.neg } } }) {
			^this.pr_inexpressible(PanolaRational(0, 1), "NaN or infinite duration");
		};
		ql = this.normalizeToRational(x);
		if (ql.isNegative) { ^this.pr_inexpressible(ql, "negative duration") };
		if (ql.isZero) { ^this.pr_spelled(ql, []) };
		if (options[\mode] == \quantize) { ql = this.quantizeToGrid(ql) };

		r = this.trySimpleDuration(ql);      if (r.notNil) { ^this.pr_spelled(ql, [r]) };
		r = this.tryDottedDuration(ql);      if (r.notNil) { ^this.pr_spelled(ql, [r]) };
		// split (tied binary/dotted notes) is tried BEFORE tuplet so a dyadic duration like 0.625
		// spells as tied notes (eighth+32nd), not an ugly tuplet; only non-dyadic values (1/3, 1/5,
		// ...) cannot be split and fall through to the tuplet step. Matches the spec Expected-examples.
		r = this.splitIntoComponents(ql);    if (r.notNil) { ^this.pr_spelled(ql, r) };
		r = this.tryTupletDuration(ql);      if (r.notNil) { ^this.pr_spelled(ql, [r]) };
		r = this.tryLargeTupletFallback(ql); if (r.notNil) { ^this.pr_spelled(ql, [r]) };
		^this.pr_inexpressible(ql, this.pr_inexpressibleReason(ql));
	}

	/*
	[method.trySimpleDuration]
	description = "a single ordinary-note-value component equal to ql, or nil"
	[method.trySimpleDuration.args]
	ql = "a quarterLength (a PanolaRational)"
	[method.trySimpleDuration.returns]
	what = "a component Event or nil"
	*/
	trySimpleDuration { | ql |
		noteTypes.do({ | e | if (this.pr_qlOf(e[0]) == ql) { ^this.pr_component(e[0], 0, ql) } });
		^nil;
	}

	/*
	[method.tryDottedDuration]
	description = "a single dotted-note component equal to ql, or nil"
	[method.tryDottedDuration.args]
	ql = "a quarterLength (a PanolaRational)"
	[method.tryDottedDuration.returns]
	what = "a component Event or nil"
	*/
	tryDottedDuration { | ql |
		var maxDots = options[\maxDots];
		noteTypes.do({ | e |
			var base = this.pr_qlOf(e[0]);
			(1..maxDots).do({ | dots |
				if (this.pr_dottedValue(base, dots) == ql) { ^this.pr_component(e[0], dots, ql) };
			});
		});
		^nil;
	}

	/*
	[method.tryTupletDuration]
	description = "a single note-under-a-tuplet component equal to ql (the best-ranked candidate), or nil"
	[method.tryTupletDuration.args]
	ql = "a quarterLength (a PanolaRational)"
	[method.tryTupletDuration.returns]
	what = "a component Event or nil"
	*/
	tryTupletDuration { | ql |
		var cands = [], maxA = options[\maxTupletActual], maxN = options[\maxTupletNormal];
		noteTypes.do({ | e |
			var base = this.pr_qlOf(e[0]);
			(2..maxA).do({ | actual |
				(1..maxN).do({ | normal |
					if ((actual != normal) and: { (base * PanolaRational(normal, actual)) == ql }) {
						cands = cands.add((name: e[0], actual: actual, normal: normal));
					};
				});
			});
		});
		if (cands.isEmpty) { ^nil };
		cands = cands.sort({ | a, b | this.pr_tupletBefore(a, b) });
		^this.pr_componentTuplet(cands[0][\name], ql, cands[0][\actual], cands[0][\normal]);
	}

	/*
	[method.pr_tupletRank]
	description = "a sort key for a tuplet candidate (common ratios first, then a power-of-two-normal preference)"
	[method.pr_tupletRank.args]
	c = "a tuplet candidate Event (name, actual, normal)"
	[method.pr_tupletRank.returns]
	what = "an Array sort key"
	*/
	pr_tupletRank { | c |
		var common = [[3,2],[5,4],[6,4],[7,4],[7,8],[5,2],[9,8],[3,4],[2,3]];
		var ci = common.indexOfEqual([c[\actual], c[\normal]]);
		^[ ci ? 999, c[\actual], c[\normal].neg, this.pr_qlOf(c[\name]).asFloat.neg ];
	}
	/*
	[method.pr_tupletBefore]
	description = "whether tuplet candidate a should sort before candidate b"
	[method.pr_tupletBefore.args]
	a = "a tuplet candidate Event"
	b = "a tuplet candidate Event"
	[method.pr_tupletBefore.returns]
	what = "a Boolean"
	*/
	pr_tupletBefore { | a, b |
		var ra = this.pr_tupletRank(a), rb = this.pr_tupletRank(b);
		ra.size.do({ | i |
			if (ra[i] < rb[i]) { ^true };
			if (ra[i] > rb[i]) { ^false };
		});
		^false;
	}
	/*
	[method.splitIntoComponents]
	description = "decompose ql into tied ordinary/dotted components, or nil if it cannot be split exactly"
	[method.splitIntoComponents.args]
	ql = "a quarterLength (a PanolaRational)"
	[method.splitIntoComponents.returns]
	what = "an Array of component Events, or nil"
	*/
	splitIntoComponents { | ql |
		var remaining = ql, comps = [], maxComp = options[\maxComponents];
		while { remaining.isZero.not } {
			var c = this.pr_findLargestAssignableAtMost(remaining);
			if (c.isNil) { ^nil };
			comps = comps.add(c);
			remaining = remaining - c[\ql];
			if (comps.size > maxComp) { ^nil };
		};
		^comps;
	}

	/*
	[method.pr_findLargestAssignableAtMost]
	description = "the largest single ordinary/dotted component not exceeding the remaining duration (used by the greedy split)"
	[method.pr_findLargestAssignableAtMost.args]
	remaining = "the remaining quarterLength (a PanolaRational)"
	[method.pr_findLargestAssignableAtMost.returns]
	what = "a component Event or nil"
	*/
	pr_findLargestAssignableAtMost { | remaining |
		var best = nil, bestQl = nil, maxDots = options[\maxDots];
		noteTypes.do({ | e |
			var base = this.pr_qlOf(e[0]);
			(0..maxDots).do({ | dots |
				var v = this.pr_dottedValue(base, dots);
				if ((v <= remaining) and: { bestQl.isNil or: { v > bestQl } }) {
					bestQl = v; best = this.pr_component(e[0], dots, v);
				};
			});
		});
		^best;
	}
	/*
	[method.tryLargeTupletFallback]
	description = "spell the whole ql as one large tuplet when allowLargeTuplets is set, or nil"
	[method.tryLargeTupletFallback.args]
	ql = "a quarterLength (a PanolaRational)"
	[method.tryLargeTupletFallback.returns]
	what = "a component Event or nil"
	*/
	tryLargeTupletFallback { | ql |
		if (options[\allowLargeTuplets].not) { ^nil };
		noteTypes.do({ | e |
			var base = this.pr_qlOf(e[0]);
			var ratio = ql / base;                 // = normal / actual
			var normal = ratio.numerator, actual = ratio.denominator;
			if ((actual != normal) and: { actual >= 1 } and: { normal >= 1 }
				and: { actual <= options[\maxLargeTupletActual] }
				and: { normal <= options[\maxLargeTupletNormal] }) {
				^this.pr_componentTuplet(e[0], ql, actual, normal);
			};
		});
		^nil;
	}
	/*
	[method.pr_inexpressibleReason]
	description = "the reason a duration cannot be spelled (too small vs. cannot decompose exactly)"
	[method.pr_inexpressibleReason.args]
	ql = "a quarterLength (a PanolaRational)"
	[method.pr_inexpressibleReason.returns]
	what = "a String"
	*/
	pr_inexpressibleReason { | ql |
		var minQl = this.pr_qlOf(options[\minNoteType]);
		if (ql < minQl) { ^"smaller than minimum supported note value" };
		^"cannot decompose exactly into assignable components";
	}
}
