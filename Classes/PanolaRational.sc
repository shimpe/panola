// Exact rational number for Panola duration spelling. num/den are stored as Float-valued
// integers (doubles are exact to 2^53, far above any intermediate here) so the arithmetic
// never overflows SuperCollider's 32-bit Integer. Always reduced; sign kept in num; den > 0.
/*
[general]
title = "PanolaRational"
summary = "an exact rational number used by Panola's duration spelling"
categories = "Utils"
related = "Classes/PanolaDurationSpeller"
description = '''
PanolaRational is a minimal exact rational (teletype::num/den::, always reduced, sign in the numerator,
denominator positive). It exists because SuperCollider has no rational type and because notation must not
be treated as floating point. Numerator and denominator are stored as Float-valued integers so the
arithmetic stays exact without overflowing SuperCollider's 32-bit Integer. It supports the strong::+ - * /::
operators and the comparison operators, plus construction from an Integer, a decimal String
(teletype::*fromDecimalString::), or a Float via a limit-denominator continued fraction
(teletype::*fromFloat::), e.g. teletype::0.3333333:: becomes teletype::1/3::.
'''
*/
PanolaRational {
	/*
	[method.num]
	description = "the numerator (a Float holding an exact integer)"
	[method.num.returns]
	what = "a Float"
	*/
	/*
	[method.den]
	description = "the denominator (a Float holding an exact positive integer)"
	[method.den.returns]
	what = "a Float"
	*/
	var <num, <den;

	/*
	[classmethod.new]
	description = "create a reduced rational num/den"
	[classmethod.new.args]
	num = "the numerator"
	den = "the denominator (default 1)"
	[classmethod.new.returns]
	what = "a PanolaRational"
	*/
	*new { | num = 0, den = 1 |
		^super.new.pr_init(num.asFloat, den.asFloat);
	}

	/*
	[classmethod.fromInteger]
	description = "an Integer as n/1"
	[classmethod.fromInteger.args]
	n = "an Integer"
	[classmethod.fromInteger.returns]
	what = "a PanolaRational"
	*/
	*fromInteger { | n | ^this.new(n.asFloat, 1.0); }

	/*
	[classmethod.pr_gcd]
	description = "the greatest common divisor of two Float-valued integers (Euclid's algorithm), used to reduce fractions"
	[classmethod.pr_gcd.args]
	a = "a Float-valued integer"
	b = "a Float-valued integer"
	[classmethod.pr_gcd.returns]
	what = "a Float (the gcd)"
	*/
	*pr_gcd { | a, b |
		a = a.abs; b = b.abs;
		while { b > 0.5 } { var t = b; b = a % b; a = t };
		^a;
	}

	/*
	[method.pr_init]
	description = "initialize this rational from a raw numerator and denominator: force the sign into the numerator (denominator positive) and reduce by the gcd"
	[method.pr_init.args]
	n = "the raw numerator (a Float)"
	d = "the raw denominator (a Float)"
	[method.pr_init.returns]
	what = "this PanolaRational"
	*/
	pr_init { | n, d |
		var g;
		if (d == 0) { Error("PanolaRational: zero denominator").throw };
		if (d < 0) { n = n.neg; d = d.neg };
		g = PanolaRational.pr_gcd(n, d);
		if (g < 0.5) { g = 1.0 };
		num = n / g;
		den = d / g;
	}

	/*
	[classmethod.pr_coerce]
	description = "coerce a value to a PanolaRational: pass a PanolaRational through, wrap an Integer as n/1, or convert a Float via fromFloat (used by the arithmetic and comparison operators)"
	[classmethod.pr_coerce.args]
	x = "a PanolaRational, Integer, or Float"
	[classmethod.pr_coerce.returns]
	what = "a PanolaRational"
	*/
	*pr_coerce { | x |
		^x.isKindOf(PanolaRational).if({ x }, {
			x.isInteger.if({ this.new(x, 1) }, { this.fromFloat(x.asFloat) })
		});
	}

	// exact dyadic fraction of a finite double, then limit the denominator
	/*
	[classmethod.fromFloat]
	description = "the exact rational of a finite Float, with the denominator limited so common values snap to their intended fraction (e.g. 0.3333333 -> 1/3, 0.4 -> 2/5)"
	[classmethod.fromFloat.args]
	x = "a finite Float"
	maxDenom = "the largest allowed denominator (default 65536)"
	[classmethod.fromFloat.returns]
	what = "a PanolaRational"
	*/
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

	/*
	[classmethod.fromDecimalString]
	description = "parse a decimal String such as \"0.625\" as an exact rational (5/8)"
	[classmethod.fromDecimalString.args]
	s = "a decimal String"
	[classmethod.fromDecimalString.returns]
	what = "a PanolaRational"
	*/
	*fromDecimalString { | s |
		var neg = (s[0] == $-), str = neg.if({ s.copyRange(1, s.size - 1) }, { s });
		var parts = str.split($.);
		var whole = parts[0].asInteger, frac = (parts.size > 1).if({ parts[1] }, { "" });
		var den = 10.pow(frac.size);
		var num = (whole * den) + ((frac.size > 0).if({ frac.asInteger }, { 0 }));
		^this.new(neg.if({ num.neg }, { num }), den);
	}

	// CPython Fraction.limit_denominator, in Float-integer arithmetic
	/*
	[method.limitDenominator]
	description = "the closest rational to this value whose denominator is at most maxDenom (CPython Fraction.limit_denominator)"
	[method.limitDenominator.args]
	maxDenom = "the largest allowed denominator (default 65536)"
	[method.limitDenominator.returns]
	what = "a PanolaRational"
	*/
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

	== { | o | (o.isKindOf(PanolaRational) or: { o.isNumber }).if(
		{ o = PanolaRational.pr_coerce(o); ^(num == o.num) and: { den == o.den } },
		{ ^false }); }
	hash { ^num.asInteger.hash bitXor: den.asInteger.hash; }
	< { | o | o = PanolaRational.pr_coerce(o); ^((num * o.den) < (o.num * den)); }
	<= { | o | ^(this < o) or: { this == o }; }
	> { | o | ^(this <= o).not; }
	>= { | o | ^(this < o).not; }

	/*
	[method.negate]
	description = "the negation of this value"
	[method.negate.returns]
	what = "a PanolaRational"
	*/
	negate { ^PanolaRational.new(num.neg, den); }
	/*
	[method.neg]
	description = "the negation of this value (alias of negate)"
	[method.neg.returns]
	what = "a PanolaRational"
	*/
	neg { ^this.negate; }
	/*
	[method.reciprocal]
	description = "den/num as a PanolaRational"
	[method.reciprocal.returns]
	what = "a PanolaRational"
	*/
	reciprocal { ^PanolaRational.new(den, num); }
	/*
	[method.abs]
	description = "the absolute value"
	[method.abs.returns]
	what = "a PanolaRational"
	*/
	abs { ^PanolaRational.new(num.abs, den); }
	/*
	[method.isNegative]
	description = "whether the value is negative"
	[method.isNegative.returns]
	what = "true if the value is < 0"
	*/
	isNegative { ^num < 0; }
	/*
	[method.isZero]
	description = "whether the value is zero"
	[method.isZero.returns]
	what = "true if the value is 0"
	*/
	isZero { ^num == 0; }
	/*
	[method.numerator]
	description = "the numerator as an Integer"
	[method.numerator.returns]
	what = "the numerator as an Integer"
	*/
	numerator { ^num.asInteger; }
	/*
	[method.denominator]
	description = "the denominator as an Integer"
	[method.denominator.returns]
	what = "the denominator as an Integer"
	*/
	denominator { ^den.asInteger; }
	/*
	[method.asFloat]
	description = "the value as a Float"
	[method.asFloat.returns]
	what = "the value as a Float"
	*/
	asFloat { ^num / den; }
	/*
	[method.asInteger]
	description = "the value truncated toward zero as an Integer"
	[method.asInteger.returns]
	what = "the value truncated toward zero as an Integer"
	*/
	asInteger { ^(num / den).asInteger; }
	/*
	[method.asString]
	description = "the value as \"num/den\""
	[method.asString.returns]
	what = "the value as \"num/den\""
	*/
	asString { ^num.asInteger.asString ++ "/" ++ den.asInteger.asString; }
	printOn { | stream | stream << "PanolaRational(" << num.asInteger << ", " << den.asInteger << ")"; }
}
