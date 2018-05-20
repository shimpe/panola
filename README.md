# panola
supercollider PAttern NOtation LAnguage (superset of subset of MISPEL ported to supercollider)

This quark provides a fairly easy way to write tonal music in supercollider using a kind of music notation.
Properties like volume and tempo automatially can be animated over time to painlessly specify things like 
crescendo/decrescendo or accelerando/ritardando. These same animated properties can also be used to drive 
synth arguments (e.g. animate resonance over time if you play the music to a synth that takes resonance as parameter).

To install, evaluate

Quarks.install("https://github.com/shimpe/panola");

in the supercollider IDE (or your favourite supercollider front end).

Simple example:
```
(
p = Panola.new("c d e f g a b");
p.asPbind.play;
)
```

Many more examples in the help for the Panola class, and a tutorial appears on http://sccode.org/1-5aq

