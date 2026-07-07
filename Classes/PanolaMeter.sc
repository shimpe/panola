// Metric boundary hierarchy for a time signature. boundaries is a strength-ranked list of
// ( offsetQL: PanolaRational, strength: Integer, label: String ), sorted by offset, one per offset
// (max strength kept). Simple / compound / additive meters. See the SP2a design doc.
PanolaMeter {
	var <numerator, <denominator, <groups, <measureLengthQL, <boundaries;

	*new { | numerator, denominator, groups | ^super.new.pr_init(numerator, denominator, groups); }

	*isCompound { | num, den | ^((num % 3) == 0) and: { num > 3 } and: { #[8, 16].includes(den) }; }

	pr_init { | num, den, grps |
		numerator = num; denominator = den; groups = grps;
		measureLengthQL = PanolaRational(num * 4, den);
		boundaries = this.pr_build;
	}

	pr_unit { ^PanolaRational(4, denominator); }              // QL of one denominator-unit

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

	pr_sortUnique { | bs |
		var dict = Dictionary.new;
		bs.do({ | b |
			var key = b[\offsetQL].asString, ex = dict[key];
			if (ex.isNil or: { b[\strength] > ex[\strength] }) { dict[key] = b };
		});
		^dict.values.sort({ | a, c | a[\offsetQL] < c[\offsetQL] });
	}
}
