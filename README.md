# data-gen

A tiny project to generate synthetic data.  Each class will be
representd by a randomly projected and translated gaussian.  Example
usage:

```console
lein run -file foo.csv -classes 100 -fields 20 -rows 10000 -seed foo
```
