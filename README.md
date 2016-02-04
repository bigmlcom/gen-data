# gen-data

A tiny project to generate synthetic data.  Each class will be
representd by a randomly projected and translated gaussian.  Example
usage:

```console
lein run --output foo.csv --classes 100 --fields 20 --rows 10000 --seed foo
```

# Parameters

The possible parameters for a `lein run` task are:

  - *--output* : The output file.
  - *--classes* : The number of classes (with associated clusters) in the data.
  - *--fields* : The number of numeric fields the data.
  - *--hidden* : The number of hidden fields. The hidden fields are
                 part of the cluster projections, but not visible in
                 the final data.
  - *--rows* : The number of rows.
  - *--seed* : A seed for random number generator.
