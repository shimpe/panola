// Meter-aware note splitting: split a note at the metrical boundaries stronger than its onset (the
// onset-strength rule), spell each piece with PanolaDurationSpeller, and tie them. Later tasks add
// tuplet-container handling, fallbacks, and an optimization pass. See the SP2a design doc.
// Notation-only, exact by default.
PanolaMeterSplitter {
	var <options;

	*new { | options | ^super.new.pr_init(options); }
	pr_init { | opts |
		options = this.class.defaultOptions;
		opts.notNil.if({ opts.keysValuesDo({ | k, v | options[k] = v }) });
	}

	*defaultOptions {
		^( splitAtMeasureBoundaries: true, splitAtBeatBoundaries: true,
			splitAtStrongSubBeatBoundaries: false, splitAtTupletBoundaries: true,
			allowSyncopation: false, maxSplitPieces: 12, greedyMinBoundaryStrength: 60,
			dotBoundaryThreshold: 80, tieCost: 10, dotCost: 2, tupletCost: 20,
			hiddenBoundaryCostFactor: 1.0, syncopationPenalty: 40,
			quantizeMode: \none, quantizeGrid: PanolaRational(1, 512), quantizeTolerance: 1e-5,
			spellingOptions: nil );
	}

	*split { | noteEvent, meter, options | ^this.new(options).split(noteEvent, meter); }

	split { | noteEvent, meter |
		var ev = this.pr_prepareInput(noteEvent);
		^(ev[\tupletContext].notNil).if(
			{ this.pr_splitTupletContained(ev, meter) },
			{ this.pr_splitBasic(ev, meter) });
	}

	pr_prepareInput { | ev |
		var onset = ev[\onsetQL] ? PanolaRational(0, 1);
		var e = ( onsetQL: onset, durationQL: ev[\durationQL], isRest: ev[\isRest] ? false,
			tupletContext: ev[\tupletContext] );
		if (options[\quantizeMode] == \grid) {
			var g = options[\quantizeGrid], tol = options[\quantizeTolerance];
			var snap = { | q | var n = (q / g).asFloat.round.asInteger, near = g * PanolaRational(n, 1);
				((near - q).abs.asFloat <= tol).if({ near }, { q }) };
			var s = snap.(onset), en = snap.(onset + ev[\durationQL]);
			e = ( onsetQL: s, durationQL: en - s, isRest: e[\isRest], tupletContext: e[\tupletContext] );
		};
		^e;
	}

	pr_onsetStrength { | onset, boundaries |
		var b = boundaries.detect({ | x | x[\offsetQL] == onset });
		^b.notNil.if({ b[\strength] }, { 0 });
	}

	pr_policyAllows { | label |
		^case
			{ #["measure-start", "measure-end"].includesEqual(label) } { options[\splitAtMeasureBoundaries] }
			{ #["beat", "compound-beat", "additive-group"].includesEqual(label) } { options[\splitAtBeatBoundaries] }
			{ #["subdivision", "eighth-subbeat"].includesEqual(label) } { options[\splitAtStrongSubBeatBoundaries] }
			{ label == "tuplet-boundary" } { options[\splitAtTupletBoundaries] }
			{ true } { false };
	}

	// split points for the span, using the onset-strength rule. onsetBoundaries (optional) is the
	// boundary set the onset is weighed against; defaults to boundaries. The tuplet-contained path
	// passes the meter boundaries only, so a note sitting on an interior tuplet grid point (metrically
	// weak) is still forced to break at the equal-strength grid lines it crosses.
	pr_splitPoints { | start, end, boundaries, onsetBoundaries |
		var onsetStr = this.pr_onsetStrength(start, onsetBoundaries ? boundaries), pts = [start];
		boundaries.do({ | b |
			if ((start < b[\offsetQL]) and: { b[\offsetQL] < end }) {
				var mandatory = (b[\strength] > onsetStr) and: { this.pr_policyAllows(b[\label]) };
				if (mandatory or: { b[\strength] >= 90 }) { pts = pts.add(b[\offsetQL]) };
			};
		});
		pts = pts.add(end);
		^this.pr_sortUniqueRationals(pts);
	}

	pr_sortUniqueRationals { | arr |
		var dict = Dictionary.new;
		arr.do({ | r | dict[r.asString] = r });
		^dict.values.sort({ | a, b | a < b });
	}

	pr_spellAndTie { | pts, ev |
		var comps = [], speller = PanolaDurationSpeller.new(options[\spellingOptions]), n = pts.size - 1;
		n.do({ | i |
			var s = pts[i], d = pts[i + 1] - pts[i];
			comps = comps.add((
				startQL: s, durationQL: d, spelling: speller.spell(d), isRest: ev[\isRest],
				tieFromPrevious: (i > 0) and: { ev[\isRest].not },
				tieToNext: (i < (n - 1)) and: { ev[\isRest].not }
			));
		});
		^comps;
	}

	pr_splitBasic { | ev, meter |
		var start = ev[\onsetQL], end = ev[\onsetQL] + ev[\durationQL];
		^this.pr_spellAndTie(this.pr_splitPoints(start, end, meter.boundaries), ev);
	}

	pr_tupletBoundaries { | tc |
		var bs = [], start = tc[\startQL], total = tc[\totalDurationQL], act = tc[\numberNotesActual];
		var unit = total / PanolaRational(act, 1);
		(0..act).do({ | i |
			var off = start + (unit * PanolaRational(i, 1));
			var str = ((i == 0) or: { i == act }).if({ 90 }, { 50 });
			bs = bs.add(( offsetQL: off, strength: str, label: "tuplet-boundary" ));
		});
		^bs;
	}

	pr_splitTupletContained { | ev, meter |
		var start = ev[\onsetQL], end = ev[\onsetQL] + ev[\durationQL];
		var merged = meter.boundaries ++ this.pr_tupletBoundaries(ev[\tupletContext]);
		^this.pr_spellAndTie(this.pr_splitPoints(start, end, merged, meter.boundaries), ev);
	}
}
