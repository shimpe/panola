MidiNoteTester : UnitTest {
	test_singlenote {
		var p = Panola.new("a");
		var q = p.midinotePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [69, nil]);
	}

	test_singlenote_otherdefault {
		var p = Panola.new("a", octave_default:"3");
		var q = p.midinotePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [57, nil]);
	}

	test_twonotes_diff_octave {
		var p = Panola.new("a3 b4");
		var q = p.midinotePattern.asStream;
		var result = 3.collect({
			q.next;
		});
		this.assertEquals(result, [57, 71, nil]);
	}

	test_remember_octaves {
		var p = Panola.new("a4 b a3 b");
		var q = p.midinotePattern.asStream;
		var result = 5.collect({
			q.next;
		});
		this.assertEquals(result, [69, 71, 57, 59, nil]);
	}

	test_modifiers {
		var p = Panola.new("a a# a- ax a--");
		var q = p.midinotePattern.asStream;
		var result = 6.collect({
			q.next;
		});
		this.assertEquals(result, [69, 70, 68, 71, 67, nil]);
	}

	test_cornercase_octavenumber {
		var p = Panola.new("c4 c-4 c--4 b4 b#4 bx4");
		var q = p.midinotePattern.asStream;
		var result = 7.collect({
			q.next;
		});
		this.assertEquals(result, [60, 59, 58, 71, 72, 73, nil]);
	}

	test_chord {
		var p = Panola.new("< c e> <c e g > <d f a>");
		var q = p.midinotePattern.asStream;
		var result = 4.collect({
			q.next;
		});
		this.assertEquals(result, [ [ 60, 64 ], [ 60, 64, 67 ], [ 62, 65, 69 ], nil]);
	}
}

NotationNoteTester : UnitTest {
		test_singlenote {
		var p = Panola.new("a");
		var q = p.notationnotePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, ["a4", nil]);
	}

	test_singlenote_otherdefault {
		var p = Panola.new("a", octave_default:"3");
		var q = p.notationnotePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, ["a3", nil]);
	}

	test_twonotes_diff_octave {
		var p = Panola.new("a3 b4");
		var q = p.notationnotePattern.asStream;
		var result = 3.collect({
			q.next;
		});
		this.assertEquals(result, ["a3", "b4", nil]);
	}

	test_remember_octaves {
		var p = Panola.new("a4 b a3 b");
		var q = p.notationnotePattern.asStream;
		var result = 5.collect({
			q.next;
		});
		this.assertEquals(result, ["a4", "b4", "a3" , "b3", nil]);
	}

	test_modifiers {
		var p = Panola.new("a a# a- ax a--");
		var q = p.notationnotePattern.asStream;
		var result = 6.collect({
			q.next;
		});
		this.assertEquals(result, ["a4", "a#4", "a-4", "ax4", "a--4", nil]);
	}

	test_cornercase_octavenumber {
		var p = Panola.new("c4 c-4 c--4 b4 b#4 bx4");
		var q = p.notationnotePattern.asStream;
		var result = 7.collect({
			q.next;
		});
		this.assertEquals(result, ["c4", "c-4", "c--4", "b4", "b#4", "bx4", nil]);
	}

	test_chord {
		var p = Panola.new("< c e> <c e g > <d f a>");
		var q = p.notationnotePattern.asStream;
		var result = 4.collect({
			q.next;
		});
		this.assertEquals(result, [ "< c4 e4 >", "< c4 e4 g4 >", "< d4 f4 a4 >", nil]);
	}
}

DurationTester : UnitTest {
	test_defaultduration {
		var p = Panola.new("c");
		var q = p.durationPattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [1/4, nil]);
	}

	test_otherdefault {
		var p = Panola.new("c", dur_default:"16");
		var q = p.durationPattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [1/16, nil]);
	}

	test_different_durations_and_remember {
		var p = Panola.new("c_4 d e_8 f e_7 d_16 c_4");
		var q = p.durationPattern.asStream;
		var result = 8.collect({
			q.next;
		});
		this.assertEquals(result, [1/4, 1/4, 1/8, 1/8, 1/7, 1/16, 1/4, nil]);
	}

	test_multiplier {
		var p = Panola.new("c_4 d e_8*2 f e_8 d*2");
		var q = p.durationPattern.asStream;
		var result = 7.collect({
			q.next;
		});
		this.assertEquals(result, [1/4, 1/4, 1/4, 1/4, 1/8, 1/4, nil]);
	}

	test_divider {
		var p = Panola.new("c_4/2 c4_4 c4/2");
		var q = p.durationPattern.asStream;
		var result = 4.collect({
			q.next;
		});
		this.assertEquals(result, [1/8, 1/4, 1/8, nil]);
	}

	test_multiplier_and_divider {
		var p = Panola.new("c_4*2/2 c_8*2/3 c*4/3 c_4");
		var q = p.durationPattern.asStream;
		var result = 5.collect({
			q.next;
		});
		this.assertEquals(result, [1/4, (1/8)*(2/3), (1/8)*(4/3), 1/4, nil]);
	}

	test_dots {
		var p = Panola.new("c_4 c_4. c_8.. c c_4");
		var q = p.durationPattern.asStream;
		var result = 6.collect({
			q.next;
		});
		this.assertEquals(result, [1/4, (1/4)+(1/8), (1/8)+(1/16)+(1/32), (1/8)+(1/16)+(1/32), 1/4, nil]);
	}
}

PropertyTester : UnitTest {
	test_default_volume {
		var p = Panola.new("c_4");
		var q = p.volumePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [0.5, nil]);
	}

	test_other_default_volume {
		var p = Panola.new("c_4", vol_default:0.3);
		var q = p.volumePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [0.3, nil]);
	}

	test_explicit_volume1 {
		var p = Panola.new("c_4\\vol{0.1}");
		var q = p.volumePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [0.1, nil]);
	}
	test_explicit_volume2 {
		var p = Panola.new("c_4\\vol[0.1]");
		var q = p.volumePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [0.1, nil]);
	}
	test_explicit_volume3 {
		var p = Panola.new("c_4\\otherproperty[0.6]\\vol{0.4}");
		var q = p.volumePattern.asStream;
		var result = 2.collect({
			q.next;
		});
		this.assertEquals(result, [0.4, nil]);
	}
	test_volume_fixed {
		var p = Panola.new("c_4\\otherproperty[0.6]\\vol[0.4]\\morefun[0.7] d e\\vol[0.6]");
		var q = p.volumePattern.asStream;
		var result = 4.collect({
			q.next;
		});
		this.assertEquals(result, [0.4, 0.4, 0.6, nil]);
	}
	test_volume_anim {
		var p = Panola.new("c_4\\otherproperty[0.6]\\vol{0.4}\\morefun[0.7] d e\\vol{0.6}");
		var q = p.volumePattern.asStream;
		var result = 4.collect({
			q.next;
		});
		this.assertEquals(result, [0.4, 0.5, 0.6, nil]);
	}
	test_mixed_props {
		var p = Panola.new("c_4\\otherproperty[6]\\vol{0.4}\\morefun[0.7] d\\otherproperty{4} e\\vol{0.6} f e g\\otherproperty[8]");
		var q = p.volumePattern.asStream;
		var result = 7.collect({
			q.next;
		});
		var r = p.customPropertyPattern("otherproperty").asStream;
		var result2 = 7.collect({
			r.next;
		});

		this.assertEquals(result, [0.4, 0.5, 0.6, 0.6, 0.6, 0.6, nil]);
		this.assertEquals(result2, [6, 4, 5, 6, 7, 8, nil]);
	}

	test_tempo {
		var p = Panola.new("c_4\\tempo[60] d e\\tempo[80] f g\\tempo{120} f e d\\tempo{150} a");
		var q = p.tempoPattern.asStream;
		var result = 10.collect({
			q.next;
		});
		this.assertEquals(result, [60/(4*60), 60/(4*60), 80/(4*60), 80/(4*60), 120/(4*60), 130/(4*60), 140/(4*60), 150/(4*60), 150/(4*60), nil]);
	}

}

PanolaTester {
	*new {
		^super.new.init();
	}

	init {
		MidiNoteTester.run;
		NotationNoteTester.run;
		DurationTester.run;
		PropertyTester.run;
	}
}