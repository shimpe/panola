// Meter-aware note splitting: split a note at the metrical boundaries stronger than its onset (the
// onset-strength rule), spell each piece with PanolaDurationSpeller, and tie them. Later tasks add
// tuplet-container handling, fallbacks, and an optimization pass. See the SP2a design doc.
// Notation-only, exact by default.
/*
[general]
title = "PanolaMeterSplitter"
summary = "split a note into tied, spelled components that respect the meter"
categories = "Notation, Utils"
related = "Classes/PanolaMeter, Classes/PanolaDurationSpeller"
description = '''
PanolaMeterSplitter takes a note (onset + duration in quarterLength, in a link::Classes/PanolaMeter::) and
splits it into tied components that respect the meter: a note may span boundaries no stronger than the one
it starts on, but must break at any stronger interior boundary (the onset-strength rule). Each piece is
spelled with link::Classes/PanolaDurationSpeller:: and the pieces are tied. Tuplet-contained notes split
on the tuplet grid, and an optimization pass avoids dots across strong boundaries and merges needless ties.
Notation-only, exact by default (quantization is opt-in).

code::
PanolaMeterSplitter.split(( onsetQL: PanolaRational(3,2), durationQL: PanolaRational(1,1) ), PanolaMeter(4,4));
// 1.5 + 1.0 in 4/4 -> eighth tied to eighth (it crosses the 2.0 half-measure)
::
'''
*/
PanolaMeterSplitter {
	/*
	[method.options]
	description = "the effective options Event (defaultOptions merged with any overrides passed to the constructor)"
	[method.options.returns]
	what = "an Event"
	*/
	var <options;

	/*
	[classmethod.new]
	description = "a splitter whose options are defaultOptions merged with the given overrides"
	[classmethod.new.args]
	options = "an Event overriding any defaultOptions keys, or nil"
	[classmethod.new.returns]
	what = "a PanolaMeterSplitter"
	*/
	*new { | options | ^super.new.pr_init(options); }
	/*
	[method.pr_init]
	description = "initialize teletype::options:: from defaultOptions, overlaying any supplied overrides key by key"
	[method.pr_init.args]
	opts = "an Event of option overrides, or nil"
	[method.pr_init.returns]
	what = "this PanolaMeterSplitter"
	*/
	pr_init { | opts |
		options = this.class.defaultOptions;
		opts.notNil.if({ opts.keysValuesDo({ | k, v | options[k] = v }) });
	}

	/*
	[classmethod.defaultOptions]
	description = "the default options Event: which boundary kinds force a split (teletype::splitAtMeasureBoundaries:: teletype::splitAtBeatBoundaries:: teletype::splitAtStrongSubBeatBoundaries:: teletype::splitAtTupletBoundaries::), the fallback threshold teletype::greedyMinBoundaryStrength::, the teletype::dotBoundaryThreshold:: for the dot-avoidance pass, the quantization settings (teletype::quantizeMode:: teletype::quantizeGrid:: teletype::quantizeTolerance::), and the teletype::spellingOptions:: forwarded to link::Classes/PanolaDurationSpeller::"
	[classmethod.defaultOptions.returns]
	what = "an Event"
	*/
	*defaultOptions {
		^( splitAtMeasureBoundaries: true, splitAtBeatBoundaries: true,
			splitAtStrongSubBeatBoundaries: false, splitAtTupletBoundaries: true,
			allowSyncopation: false, maxSplitPieces: 12, greedyMinBoundaryStrength: 60,
			dotBoundaryThreshold: 80, tieCost: 10, dotCost: 2, tupletCost: 20,
			hiddenBoundaryCostFactor: 1.0, syncopationPenalty: 40,
			quantizeMode: \none, quantizeGrid: PanolaRational(1, 512), quantizeTolerance: 1e-5,
			spellingOptions: nil );
	}

	/*
	[classmethod.split]
	description = "convenience: build a splitter with the given options and split a note in one call"
	[classmethod.split.args]
	noteEvent = "an Event with strong::onsetQL:: (a link::Classes/PanolaRational::, default 0), strong::durationQL:: (a PanolaRational), an optional strong::isRest:: (a Boolean), and an optional strong::tupletContext:: (teletype::startQL::, teletype::totalDurationQL::, teletype::numberNotesActual::, teletype::numberNotesNormal::) for a tuplet-contained note"
	meter = "a link::Classes/PanolaMeter::"
	options = "an Event overriding any defaultOptions keys, or nil"
	[classmethod.split.returns]
	what = "an Array of SplitComponent Events, each with teletype::startQL::, teletype::durationQL::, teletype::spelling:: (from link::Classes/PanolaDurationSpeller::), teletype::isRest::, and the tie flags teletype::tieFromPrevious:: / teletype::tieToNext::"
	*/
	*split { | noteEvent, meter, options | ^this.new(options).split(noteEvent, meter); }

	/*
	[method.split]
	description = "split a note into tied, spelled components that respect the meter. It applies the onset-strength rule (a piece may span boundaries no stronger than the one it starts on, but must break at any stronger interior boundary), routes tuplet-contained notes onto the tuplet grid, and runs the optimization pass"
	[method.split.args]
	noteEvent = "an Event with strong::onsetQL:: (a link::Classes/PanolaRational::, default 0), strong::durationQL:: (a PanolaRational), an optional strong::isRest:: (a Boolean), and an optional strong::tupletContext:: (teletype::startQL::, teletype::totalDurationQL::, teletype::numberNotesActual::, teletype::numberNotesNormal::) for a tuplet-contained note"
	meter = "a link::Classes/PanolaMeter::"
	[method.split.returns]
	what = "an Array of SplitComponent Events, each with teletype::startQL::, teletype::durationQL::, teletype::spelling:: (from link::Classes/PanolaDurationSpeller::), teletype::isRest::, and the tie flags teletype::tieFromPrevious:: / teletype::tieToNext::"
	*/
	split { | noteEvent, meter |
		var ev = this.pr_prepareInput(noteEvent);
		var comps = (ev[\tupletContext].notNil).if(
			{ this.pr_splitTupletContained(ev, meter) },
			{ this.pr_splitBasic(ev, meter) });
		^this.pr_optimize(comps, ev, meter);
	}

	/*
	[method.pr_prepareInput]
	description = "normalize a note event (default the onset to 0 and teletype::isRest:: to false, carry the tuplet context) and, when teletype::quantizeMode:: is teletype::\\grid::, snap the onset and end to the quantize grid within the tolerance"
	[method.pr_prepareInput.args]
	ev = "the raw note Event (onsetQL, durationQL, optional isRest, optional tupletContext)"
	[method.pr_prepareInput.returns]
	what = "a normalized note Event"
	*/
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

	/*
	[method.pr_onsetStrength]
	description = "the strength of the boundary lying exactly at the given onset, or 0 when the onset falls on no boundary (a metrically weak onset)"
	[method.pr_onsetStrength.args]
	onset = "the onset offset (a link::Classes/PanolaRational::)"
	boundaries = "the boundary Events to search"
	[method.pr_onsetStrength.returns]
	what = "an Integer strength"
	*/
	pr_onsetStrength { | onset, boundaries |
		var b = boundaries.detect({ | x | x[\offsetQL] == onset });
		^b.notNil.if({ b[\strength] }, { 0 });
	}

	/*
	[method.pr_policyAllows]
	description = "whether the current options permit a split at a boundary of the given label kind (measure/beat/subdivision/tuplet), consulting the matching teletype::splitAt...Boundaries:: option"
	[method.pr_policyAllows.args]
	label = "the boundary label String (e.g. teletype::measure-start::, teletype::beat::, teletype::eighth-subbeat::, teletype::tuplet-boundary::)"
	[method.pr_policyAllows.returns]
	what = "a Boolean"
	*/
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
	/*
	[method.pr_splitPoints]
	description = "the sorted, unique split offsets for the span from start to end, applying the onset-strength rule: an interior boundary forces a split when it is stronger than the onset strength and its kind is policy-allowed, or when it is at least strength 90"
	[method.pr_splitPoints.args]
	start = "the span start offset (a link::Classes/PanolaRational::)"
	end = "the span end offset (a link::Classes/PanolaRational::)"
	boundaries = "the boundary Events to consider as candidate split points"
	onsetBoundaries = "the boundary set the onset strength is measured against (optional; defaults to teletype::boundaries::). The tuplet-contained path passes the meter boundaries only, so a note sitting on an interior tuplet grid point (metrically weak) is still forced to break at the equal-strength grid lines it crosses"
	[method.pr_splitPoints.returns]
	what = "an Array of split offsets (link::Classes/PanolaRational::) from start to end inclusive, sorted and unique"
	*/
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

	/*
	[method.pr_sortUniqueRationals]
	description = "deduplicate an Array of link::Classes/PanolaRational:: values by their exact string form and return them sorted ascending"
	[method.pr_sortUniqueRationals.args]
	arr = "an Array of PanolaRationals"
	[method.pr_sortUniqueRationals.returns]
	what = "a sorted Array of unique PanolaRationals"
	*/
	pr_sortUniqueRationals { | arr |
		var dict = Dictionary.new;
		arr.do({ | r | dict[r.asString] = r });
		^dict.values.sort({ | a, b | a < b });
	}

	/*
	[method.pr_spellAndTie]
	description = "spell each consecutive gap between the split points with link::Classes/PanolaDurationSpeller:: and build the SplitComponent list, tying interior pieces together (never tying rests)"
	[method.pr_spellAndTie.args]
	pts = "the sorted split offsets (an Array of link::Classes/PanolaRational::)"
	ev = "the note Event (supplies teletype::isRest::)"
	[method.pr_spellAndTie.returns]
	what = "an Array of SplitComponent Events (teletype::startQL::, teletype::durationQL::, teletype::spelling::, teletype::isRest::, teletype::tieFromPrevious::, teletype::tieToNext::)"
	*/
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

	/*
	[method.pr_splitBasic]
	description = "split a non-tuplet note at the meter boundaries (via the onset-strength rule) and spell/tie the pieces; if any piece is inexpressible, defer to the fallback chain"
	[method.pr_splitBasic.args]
	ev = "the prepared note Event"
	meter = "a link::Classes/PanolaMeter::"
	[method.pr_splitBasic.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_splitBasic { | ev, meter |
		var start = ev[\onsetQL], end = ev[\onsetQL] + ev[\durationQL];
		var comps = this.pr_spellAndTie(this.pr_splitPoints(start, end, meter.boundaries), ev);
		^this.pr_allSpellable(comps).if({ comps }, { this.pr_fallback(ev, meter) });
	}

	/*
	[method.pr_allSpellable]
	description = "whether every component spelled to an expressible notation (none is teletype::inexpressible::)"
	[method.pr_allSpellable.args]
	comps = "an Array of SplitComponent Events"
	[method.pr_allSpellable.returns]
	what = "a Boolean"
	*/
	pr_allSpellable { | comps | ^comps.every({ | c | c[\spelling][\inexpressible].not }); }

	/*
	[method.pr_fallback]
	description = "the fallback chain for an unspellable basic split: try the greedy split first, then the smallest-grid split if that still leaves an inexpressible piece"
	[method.pr_fallback.args]
	ev = "the prepared note Event"
	meter = "a link::Classes/PanolaMeter::"
	[method.pr_fallback.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_fallback { | ev, meter |
		var comps = this.pr_fallbackAggressive(ev, meter);
		^this.pr_allSpellable(comps).if({ comps }, { this.pr_splitAtSmallestGrid(ev) });
	}

	/*
	[method.pr_fallbackAggressive]
	description = "greedy fallback: split at every meter boundary at least teletype::greedyMinBoundaryStrength:: strong, then spell and tie the pieces"
	[method.pr_fallbackAggressive.args]
	ev = "the prepared note Event"
	meter = "a link::Classes/PanolaMeter::"
	[method.pr_fallbackAggressive.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_fallbackAggressive { | ev, meter |
		var start = ev[\onsetQL], end = ev[\onsetQL] + ev[\durationQL], minS = options[\greedyMinBoundaryStrength];
		var pts = [start];
		meter.boundaries.do({ | b |
			if ((start < b[\offsetQL]) and: { b[\offsetQL] < end } and: { b[\strength] >= minS }) {
				pts = pts.add(b[\offsetQL]);
			};
		});
		pts = pts.add(end);
		^this.pr_spellAndTie(this.pr_sortUniqueRationals(pts), ev);
	}

	/*
	[method.pr_splitAtSmallestGrid]
	description = "last-resort fallback: split the span into equal pieces of the smallest allowed note type, then spell and tie them (guarantees an expressible, sum-exact result)"
	[method.pr_splitAtSmallestGrid.args]
	ev = "the prepared note Event"
	[method.pr_splitAtSmallestGrid.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_splitAtSmallestGrid { | ev |
		var grid = this.pr_minNoteTypeQL, start = ev[\onsetQL], end = ev[\onsetQL] + ev[\durationQL];
		var pts = [start], cur = start + grid;
		while { cur < end } { pts = pts.add(cur); cur = cur + grid };
		pts = pts.add(end);
		^this.pr_spellAndTie(this.pr_sortUniqueRationals(pts), ev);
	}

	/*
	[method.pr_minNoteTypeQL]
	description = "the quarterLength of the smallest allowed note type, taken from teletype::spellingOptions[\\minNoteType]:: when set, otherwise teletype::2048th::"
	[method.pr_minNoteTypeQL.returns]
	what = "a link::Classes/PanolaRational::"
	*/
	pr_minNoteTypeQL {
		var so = options[\spellingOptions], name = (so.notNil and: { so[\minNoteType].notNil }).if(
			{ so[\minNoteType] }, { '2048th' });
		^PanolaDurationSpeller.new.pr_qlOf(name);
	}

	/*
	[method.pr_tupletBoundaries]
	description = "the tuplet grid boundaries for a tuplet context: one boundary at each of the teletype::numberNotesActual:: subdivisions, with the endpoints at strength 90 and the interior grid lines at strength 50, all labelled teletype::tuplet-boundary::"
	[method.pr_tupletBoundaries.args]
	tc = "the tuplet context Event (teletype::startQL::, teletype::totalDurationQL::, teletype::numberNotesActual::)"
	[method.pr_tupletBoundaries.returns]
	what = "an Array of boundary Events"
	*/
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

	/*
	[method.pr_splitTupletContained]
	description = "split a tuplet-contained note on the merged meter-plus-tuplet grid, measuring the onset strength against the meter only so a metrically weak mid-tuplet onset still breaks at the tuplet grid lines it crosses"
	[method.pr_splitTupletContained.args]
	ev = "the prepared note Event (with a teletype::tupletContext::)"
	meter = "a link::Classes/PanolaMeter::"
	[method.pr_splitTupletContained.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_splitTupletContained { | ev, meter |
		var start = ev[\onsetQL], end = ev[\onsetQL] + ev[\durationQL];
		var merged = meter.boundaries ++ this.pr_tupletBoundaries(ev[\tupletContext]);
		^this.pr_spellAndTie(this.pr_splitPoints(start, end, merged, meter.boundaries), ev);
	}

	/*
	[method.pr_optimize]
	description = "the optimization pass over a split result: first re-split components whose dotted spelling hides a strong boundary, then merge adjacent components where it is safe"
	[method.pr_optimize.args]
	comps = "an Array of SplitComponent Events"
	ev = "the prepared note Event"
	meter = "a link::Classes/PanolaMeter::"
	[method.pr_optimize.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_optimize { | comps, ev, meter |
		comps = this.pr_avoidDotsAcrossStrong(comps, ev, meter);
		comps = this.pr_mergeIfSafe(comps, ev, meter);
		^comps;
	}

	// re-split a component whose (single) dotted spelling hides a boundary >= dotBoundaryThreshold
	/*
	[method.pr_avoidDotsAcrossStrong]
	description = "re-split any component whose single dotted spelling hides an interior boundary at least teletype::dotBoundaryThreshold:: strong, splitting that piece at the meter boundaries and carrying the original piece's tie flags onto the new outer edges"
	[method.pr_avoidDotsAcrossStrong.args]
	comps = "an Array of SplitComponent Events"
	ev = "the prepared note Event"
	meter = "a link::Classes/PanolaMeter::"
	[method.pr_avoidDotsAcrossStrong.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_avoidDotsAcrossStrong { | comps, ev, meter |
		var out = [];
		comps.do({ | c |
			var sp = c[\spelling], dotted = (sp[\inexpressible].not) and: { sp[\components].size == 1 }
				and: { sp[\components][0][\dots] > 0 };
			var cStart = c[\startQL], cEnd = c[\startQL] + c[\durationQL];
			var hidesStrong = meter.boundaries.any({ | b |
				(cStart < b[\offsetQL]) and: { b[\offsetQL] < cEnd } and: { b[\strength] >= options[\dotBoundaryThreshold] } });
			if (dotted and: { hidesStrong }) {
				// split this piece as its own note at the meter boundaries and inherit c's tie flags
				var sub = this.pr_splitPoints(cStart, cEnd, meter.boundaries);
				var subComps = this.pr_spellAndTie(sub, ev);
				// fix outer tie flags: first inherits tieFromPrevious, last inherits tieToNext
				subComps = subComps.collect({ | sc, i |
					var e = sc.copy;
					if (ev[\isRest].not) {
						e[\tieFromPrevious] = (i > 0).if({ true }, { c[\tieFromPrevious] });
						e[\tieToNext] = (i < (subComps.size - 1)).if({ true }, { c[\tieToNext] });
					};
					e;
				});
				out = out ++ subComps;
			} {
				out = out.add(c);
			};
		});
		^out;
	}

	// merge two adjacent pieces when the merged span hides no strong boundary and spells cleanly
	/*
	[method.pr_mergeIfSafe]
	description = "merge two adjacent components when the merged span hides no boundary stronger than the note's true onset strength and spells as a single value, so it is the exact inverse of the split and never recombines across a boundary the split forced"
	[method.pr_mergeIfSafe.args]
	comps = "an Array of SplitComponent Events"
	ev = "the prepared note Event (supplies the reference onset)"
	meter = "a link::Classes/PanolaMeter::"
	[method.pr_mergeIfSafe.returns]
	what = "an Array of SplitComponent Events"
	*/
	pr_mergeIfSafe { | comps, ev, meter |
		var out = [], i = 0, speller = PanolaDurationSpeller.new(options[\spellingOptions]);
		while { i < comps.size } {
			var cur = comps[i], merged = false;
			if ((i + 1) < comps.size) {
				var nxt = comps[i + 1];
				var mStart = cur[\startQL], mEnd = nxt[\startQL] + nxt[\durationQL], mDur = mEnd - mStart;
				// weigh hidden boundaries against the NOTE's true onset strength (the reference the
				// split used), not the merged fragment's local start -- otherwise an interior fragment
				// that begins on a strong boundary inflates the reference and re-hides a same-strength
				// boundary the note's real onset had forced a split at (e.g. 7/8[2,2,3] e+q+e).
				var mOnsetStr = this.pr_onsetStrength(ev[\onsetQL], meter.boundaries);
				var hidesStrong = meter.boundaries.any({ | b |
					(mStart < b[\offsetQL]) and: { b[\offsetQL] < mEnd } and: { b[\strength] > mOnsetStr } });
				var sp = speller.spell(mDur);
				if (hidesStrong.not and: { sp[\inexpressible].not } and: { sp[\components].size == 1 }) {
					out = out.add(( startQL: mStart, durationQL: mDur, spelling: sp, isRest: ev[\isRest],
						tieFromPrevious: cur[\tieFromPrevious], tieToNext: nxt[\tieToNext] ));
					i = i + 2; merged = true;
				};
			};
			if (merged.not) { out = out.add(cur); i = i + 1 };
		};
		^out;
	}
}
