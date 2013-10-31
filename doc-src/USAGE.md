## Usage

The `forever` configuration does not replace the system init as PID 1.

The `server-spec` function provides a convenient pallet server spec for
forever.  It takes a single map as an argument, specifying configuration
choices, as described below for the `settings` function.  You can use this
in your own group or server specs in the :extends clause.

```clj
(require '[pallet.crate.forever :as forever])
(group-spec my-forever-group
  :extends [(forever/server-spec {})])
```

While `server-spec` provides an all-in-one function, you can use the individual
plan functions as you see fit.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with forever.

The `install` function is responsible for actually installing forever.

The `configure` function writes the forever configuration file, using the form
passed to the :config key in the `settings` function.

To create a forever job, you can write a method for
[`supervisor-config-map`](http://palletops.com/api/0.8/pallet.crate.service.html#var-supervisor-config-map).
