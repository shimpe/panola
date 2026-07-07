// Metric boundary hierarchy for a time signature. boundaries is a strength-ranked list of
// ( offsetQL: PanolaRational, strength: Integer, label: String ), sorted by offset, one per offset
// (max strength kept). Simple / compound / additive meters. See the SP2a design doc.
/*
[general]
title = "PanolaMeter"
summary = "the metric boundary hierarchy of a time signature"
categories = "Notation, Utils"
related = "Classes/PanolaMeterSplitter, Classes/PanolaDurationSpeller"
description = '''
PanolaMeter turns a time signature into a strength-ranked list of metric boundaries (each an offset in
quarterLength with an integer strength and a label), used by link::Classes/PanolaMeterSplitter:: to decide
where a note must break to respect the meter. Stronger boundaries (measure 100, the 4/4 half-measure 80,
compound beats 70, additive groups 75, ordinary beats 60, subdivisions 30-40) matter more. It handles
simple, compound (teletype::6/8:: teletype::9/8:: teletype::12/8::), and additive meters
(teletype::PanolaMeter(7, 8, [2,2,3])::). All offsets are exact link::Classes/PanolaRational::.
'''
*/
PanolaMeter {
	/*
	[method.numerator]
	description = "the time signature numerator (the number of beats per measure), e.g. 4 in 4/4"
	[method.numerator.returns]
	what = "an Integer"
	*/
	/*
	[method.denominator]
	description = "the time signature denominator (the beat note value), e.g. 4 in 4/4"
	[method.denominator.returns]
	what = "an Integer"
	*/
	/*
	[method.groups]
	description = "for an additive meter, the Array of denominator-unit counts per group (e.g. teletype::[2,2,3]:: for 7/8); nil for simple and compound meters"
	[method.groups.returns]
	what = "an Array of Integers, or nil"
	*/
	/*
	[method.measureLengthQL]
	description = "the length of one measure in quarterLength"
	[method.measureLengthQL.returns]
	what = "a link::Classes/PanolaRational::"
	*/
	/*
	[method.boundaries]
	description = "the strength-ranked metric boundary list: Events of the form teletype::(offsetQL: PanolaRational, strength: Integer, label: String):: sorted by offset, one per offset (the strongest is kept at each)"
	[method.boundaries.returns]
	what = "an Array of boundary Events sorted by offset"
	*/
	var <numerator, <denominator, <groups, <measureLengthQL, <boundaries;

	/*
	[classmethod.new]
	description = "build a meter for a time signature (numerator/denominator); pass groups only for an additive meter"
	[classmethod.new.args]
	numerator = "the time signature numerator (beats per measure)"
	denominator = "the time signature denominator (the beat note value: 4, 8, 16, ...)"
	groups = "for an additive meter, an Array of denominator-unit counts per group (e.g. teletype::[2,2,3]:: for 7/8); nil for simple and compound meters"
	[classmethod.new.returns]
	what = "a PanolaMeter"
	*/
	*new { | numerator, denominator, groups | ^super.new.pr_init(numerator, denominator, groups); }

	/*
	[classmethod.isCompound]
	description = "whether a meter is compound: the numerator is divisible by 3 and greater than 3, and the denominator is 8 or 16"
	[classmethod.isCompound.args]
	num = "the numerator"
	den = "the denominator"
	[classmethod.isCompound.returns]
	what = "a Boolean"
	*/
	*isCompound { | num, den | ^((num % 3) == 0) and: { num > 3 } and: { #[8, 16].includes(den) }; }

	/*
	[method.pr_init]
	description = "initialize the meter: store the numerator, denominator and groups, compute the measure length, and build the boundary list"
	[method.pr_init.args]
	num = "the numerator"
	den = "the denominator"
	grps = "the additive groups Array, or nil"
	[method.pr_init.returns]
	what = "this PanolaMeter"
	*/
	pr_init { | num, den, grps |
		numerator = num; denominator = den; groups = grps;
		measureLengthQL = PanolaRational(num * 4, den);
		boundaries = this.pr_build;
	}

	/*
	[method.pr_unit]
	description = "the quarterLength of one denominator-unit (teletype::4 / denominator::), e.g. 0.5 for an eighth in an /8 meter"
	[method.pr_unit.returns]
	what = "a link::Classes/PanolaRational::"
	*/
	pr_unit { ^PanolaRational(4, denominator); }              // QL of one denominator-unit

	/*
	[method.pr_build]
	description = "build the strength-ranked boundary list: add the measure-start and measure-end boundaries, then the interior boundaries for a simple, compound, or additive meter, then deduplicate and sort"
	[method.pr_build.returns]
	what = "an Array of boundary Events sorted by offset"
	*/
	pr_build {
		var bs = List.new, unit = this.pr_unit;
		bs.add(( offsetQL: PanolaRational(0, 1),   strength: 100, label: "measure-start" ));
		bs.add(( offsetQL: measureLengthQL,        strength: 100, label: "measure-end" ));
		if (groups.notNil) { this.pr_additive(bs, unit) } {
			if (PanolaMeter.isCompound(numerator, denominator)) { this.pr_compound(bs, unit) }
			{ this.pr_simple(bs, unit) };
		};
		^this.pr_sortUnique(bs);
	}

	/*
	[method.pr_simple]
	description = "append the beat boundaries (with the 4/4 half-measure and 2/x weightings) and the half-unit subdivisions for a simple meter to the boundary list"
	[method.pr_simple.args]
	bs = "the List of boundaries being built (mutated in place)"
	unit = "the quarterLength of one denominator-unit"
	[method.pr_simple.returns]
	what = "nothing meaningful (it appends to bs)"
	*/
	pr_simple { | bs, unit |
		var half = unit / PanolaRational(2, 1), nHalf;
		(1..(numerator - 1)).do({ | bi |
			var str = (numerator == 4).if({ (bi == 2).if({ 80 }, { 60 }) },
				{ (numerator == 2).if({ 70 }, { 60 }) });
			bs.add(( offsetQL: unit * PanolaRational(bi, 1), strength: str, label: "beat" ));
		});
		nHalf = (measureLengthQL / half).asInteger;
		(1..(nHalf - 1)).do({ | i |
			bs.add(( offsetQL: half * PanolaRational(i, 1), strength: 30, label: "subdivision" ));
		});
	}

	/*
	[method.pr_compound]
	description = "append the compound-beat boundaries (every three denominator-units) and the eighth-subbeat boundaries for a compound meter to the boundary list"
	[method.pr_compound.args]
	bs = "the List of boundaries being built (mutated in place)"
	unit = "the quarterLength of one denominator-unit"
	[method.pr_compound.returns]
	what = "nothing meaningful (it appends to bs)"
	*/
	pr_compound { | bs, unit |
		var beatLen = unit * PanolaRational(3, 1), beatCount = numerator div: 3, nUnit;
		(1..(beatCount - 1)).do({ | bi |
			bs.add(( offsetQL: beatLen * PanolaRational(bi, 1), strength: 70, label: "compound-beat" ));
		});
		nUnit = (measureLengthQL / unit).asInteger;
		(1..(nUnit - 1)).do({ | i |
			bs.add(( offsetQL: unit * PanolaRational(i, 1), strength: 40, label: "eighth-subbeat" ));
		});
	}

	/*
	[method.pr_additive]
	description = "append the additive-group boundaries (at the end of each group except the last) and the within-group subdivisions for an additive meter (following the groups Array) to the boundary list"
	[method.pr_additive.args]
	bs = "the List of boundaries being built (mutated in place)"
	unit = "the quarterLength of one denominator-unit"
	[method.pr_additive.returns]
	what = "nothing meaningful (it appends to bs)"
	*/
	pr_additive { | bs, unit |
		var offset = PanolaRational(0, 1);
		groups.do({ | g, gi |
			var groupStart = offset;
			offset = offset + (unit * PanolaRational(g, 1));
			if (gi < (groups.size - 1)) {
				bs.add(( offsetQL: offset, strength: 75, label: "additive-group" ));
			};
			(1..(g - 1)).do({ | j |
				bs.add(( offsetQL: groupStart + (unit * PanolaRational(j, 1)), strength: 40, label: "subdivision" ));
			});
		});
	}

	/*
	[method.pr_sortUnique]
	description = "deduplicate the boundaries by offset (keeping the strongest boundary at each offset) and return them sorted by ascending offset"
	[method.pr_sortUnique.args]
	bs = "the List of boundaries to deduplicate"
	[method.pr_sortUnique.returns]
	what = "an Array of boundary Events sorted by offset"
	*/
	pr_sortUnique { | bs |
		var dict = Dictionary.new;
		bs.do({ | b |
			var key = b[\offsetQL].asString, ex = dict[key];
			if (ex.isNil or: { b[\strength] > ex[\strength] }) { dict[key] = b };
		});
		^dict.values.sort({ | a, c | a[\offsetQL] < c[\offsetQL] });
	}
}
