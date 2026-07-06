// Exact rational number for Panola duration spelling. num/den are stored as Float-valued
// integers (doubles are exact to 2^53, far above any intermediate here) so the arithmetic
// never overflows SuperCollider's 32-bit Integer. Always reduced; sign kept in num; den > 0.
PanolaRational {
	var <num, <den;

	*new { | num = 0, den = 1 |
		^super.new.pr_init(num.asFloat, den.asFloat);
	}

	*fromInteger { | n | ^this.new(n.asFloat, 1.0); }

	*pr_gcd { | a, b |
		a = a.abs; b = b.abs;
		while { b > 0.5 } { var t = b; b = a % b; a = t };
		^a;
	}

	pr_init { | n, d |
		var g;
		if (d == 0) { Error("PanolaRational: zero denominator").throw };
		if (d < 0) { n = n.neg; d = d.neg };
		g = PanolaRational.pr_gcd(n, d);
		if (g < 0.5) { g = 1.0 };
		num = n / g;
		den = d / g;
	}

	*pr_coerce { | x |
		^x.isKindOf(PanolaRational).if({ x }, {
			x.isInteger.if({ this.new(x, 1) }, { this.fromFloat(x.asFloat) })
		});
	}

	// exact dyadic fraction of a finite double, then limit the denominator
	*fromFloat { | x, maxDenom = 65536 |
		var n, d = 1.0, r;
		if (x.isKindOf(Float) and: { x.isNaN or: { (x == inf) or: { x == inf.neg } } }) {
			Error("PanolaRational.fromFloat: non-finite").throw;
		};
		n = x.asFloat;
		while { (n.frac != 0) and: { d < 1.15e18 } } { n = n * 2; d = d * 2 };
		r = this.new(n, d);
		^(r.den > maxDenom).if({ r.limitDenominator(maxDenom) }, { r });
	}

	*fromDecimalString { | s |
		var neg = (s[0] == $-), str = neg.if({ s.copyRange(1, s.size - 1) }, { s });
		var parts = str.split($.);
		var whole = parts[0].asInteger, frac = (parts.size > 1).if({ parts[1] }, { "" });
		var den = 10.pow(frac.size);
		var num = (whole * den) + ((frac.size > 0).if({ frac.asInteger }, { 0 }));
		^this.new(neg.if({ num.neg }, { num }), den);
	}

	// CPython Fraction.limit_denominator, in Float-integer arithmetic
	limitDenominator { | maxDenom = 65536 |
		var p0 = 0.0, q0 = 1.0, p1 = 1.0, q1 = 0.0, n = num, d = den, a, q2, k, b1, b2, running = true;
		if (maxDenom < 1) { Error("limitDenominator: maxDenom < 1").throw };
		if (den <= maxDenom) { ^this };
		while { running } {
			a = (n / d).floor;
			q2 = q0 + (a * q1);
			if (q2 > maxDenom) { running = false } {
				# p0, q0, p1, q1 = [p1, q1, p0 + (a * p1), q2];
				# n, d = [d, n - (a * d)];
			};
		};
		k = ((maxDenom - q0) / q1).floor;
		b1 = PanolaRational.new(p0 + (k * p1), q0 + (k * q1));
		b2 = PanolaRational.new(p1, q1);
		^((b2 - this).abs <= (b1 - this).abs).if({ b2 }, { b1 });
	}

	+ { | o | o = PanolaRational.pr_coerce(o); ^PanolaRational.new((num * o.den) + (o.num * den), den * o.den); }
	- { | o | o = PanolaRational.pr_coerce(o); ^PanolaRational.new((num * o.den) - (o.num * den), den * o.den); }
	* { | o | o = PanolaRational.pr_coerce(o); ^PanolaRational.new(num * o.num, den * o.den); }
	/ { | o | o = PanolaRational.pr_coerce(o); ^PanolaRational.new(num * o.den, den * o.num); }

	== { | o | o = PanolaRational.pr_coerce(o); ^(num == o.num) and: { den == o.den }; }
	hash { ^num.asInteger.hash bitXor: den.asInteger.hash; }
	< { | o | o = PanolaRational.pr_coerce(o); ^((num * o.den) < (o.num * den)); }
	<= { | o | ^(this < o) or: { this == o }; }
	> { | o | ^(this <= o).not; }
	>= { | o | ^(this < o).not; }

	negate { ^PanolaRational.new(num.neg, den); }
	reciprocal { ^PanolaRational.new(den, num); }
	abs { ^PanolaRational.new(num.abs, den); }
	isNegative { ^num < 0; }
	isZero { ^num == 0; }
	numerator { ^num.asInteger; }
	denominator { ^den.asInteger; }
	asFloat { ^num / den; }
	asInteger { ^(num / den).asInteger; }
	asString { ^num.asInteger.asString ++ "/" ++ den.asInteger.asString; }
	printOn { | stream | stream << "PanolaRational(" << num.asInteger << ", " << den.asInteger << ")"; }
}
