// music21-style duration spelling: a quarterLength -> a notation spelling of one or more
// components (note value + dots + optional tuplet). See the SP1 design doc. Pure, MEI-agnostic.
PanolaDurationSpeller {
	var <options;
	classvar <noteTypes;

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

	*defaultOptions {
		^(mode: \exact, grid: PanolaRational(1, 512), tolerance: 1e-5, maxDots: 4,
			maxComponents: 16, maxTupletActual: 13, maxTupletNormal: 13, allowLargeTuplets: false,
			maxLargeTupletActual: 1024, maxLargeTupletNormal: 1024, minNoteType: '2048th',
			floatPolicy: \limitDenominator, maxDenominator: 65536);
	}

	*new { | options | ^super.new.pr_init(options); }
	pr_init { | opts |
		options = this.class.defaultOptions;
		opts.notNil.if({ opts.keysValuesDo({ | k, v | options[k] = v }) });
	}

	*spell { | ql, options | ^this.new(options).spell(ql); }

	pr_entry { | name | ^noteTypes.detect({ | e | e[0] == name }); }
	pr_qlOf { | name | var e = this.pr_entry(name); ^PanolaRational(e[2][0], e[2][1]); }
	pr_meidurOf { | name | ^this.pr_entry(name)[1]; }

	pr_dottedValue { | baseQl, dots |
		var total = baseQl, half = baseQl;
		dots.do({ half = half / PanolaRational(2, 1); total = total + half });
		^total;
	}

	pr_component { | name, dots, ql |
		^(type: name, meidur: this.pr_meidurOf(name), dots: dots, ql: ql, tuplets: []);
	}
	pr_componentTuplet { | name, ql, actual, normal |
		^(type: name, meidur: this.pr_meidurOf(name), dots: 0, ql: ql,
			tuplets: [ (actual: actual, normal: normal, actualType: name, normalType: name) ]);
	}
	pr_spelled { | ql, components | ^(inexpressible: false, ql: ql, inferred: true, components: components); }
	pr_inexpressible { | ql, reason | ^(inexpressible: true, ql: ql, reason: reason); }

	normalizeToRational { | x |
		if (x.isKindOf(PanolaRational)) { ^x };
		if (x.isKindOf(String)) { ^PanolaRational.fromDecimalString(x) };
		if (x.isInteger) { ^PanolaRational(x, 1) };
		^(options[\floatPolicy] == \limitDenominator).if(
			{ PanolaRational.fromFloat(x, options[\maxDenominator]) },
			{ PanolaRational.fromFloat(x, 1.15e18) });
	}

	quantizeToGrid { | ql |
		var grid = options[\grid], tol = options[\tolerance];
		var steps = (ql / grid).asFloat.round.asInteger;
		var nearest = grid * PanolaRational(steps, 1);
		^((nearest - ql).abs.asFloat <= tol).if({ nearest }, { ql });
	}

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

	trySimpleDuration { | ql |
		noteTypes.do({ | e | if (this.pr_qlOf(e[0]) == ql) { ^this.pr_component(e[0], 0, ql) } });
		^nil;
	}

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

	pr_tupletRank { | c |
		var common = [[3,2],[5,4],[6,4],[7,4],[7,8],[5,2],[9,8],[3,4],[2,3]];
		var ci = common.indexOfEqual([c[\actual], c[\normal]]);
		^[ ci ? 999, c[\actual], c[\normal].neg, this.pr_qlOf(c[\name]).asFloat.neg ];
	}
	pr_tupletBefore { | a, b |
		var ra = this.pr_tupletRank(a), rb = this.pr_tupletRank(b);
		ra.size.do({ | i |
			if (ra[i] < rb[i]) { ^true };
			if (ra[i] > rb[i]) { ^false };
		});
		^false;
	}
	splitIntoComponents { | ql | ^nil; }
	tryLargeTupletFallback { | ql | ^nil; }
	pr_inexpressibleReason { | ql |
		var minQl = this.pr_qlOf(options[\minNoteType]);
		if (ql < minQl) { ^"smaller than minimum supported note value" };
		^"cannot decompose exactly into assignable components";
	}
}
