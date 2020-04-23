# Stats

Generates simple statistics (min, max, average) from numbers in
stdout, replacing inline for analysis.

## Synopsis

Suppose you have the following program that outputs numbers, as in:

    $ perl measurements.pl | grep users | head
    found 4 of 16 users,
    found 1 of 12 users,
    found 1 of 16 users,
    found 0 of 6 users,
    found 1 of 11 users,
    found 4 of 15 users,
    found 4 of 7 users,
    found 2 of 22 users,
    found 2 of 10 users,
    found 2 of 13 users,

If you place the `stats` program inline, it will replace the logline
with the read value, the minimum value seen, and the maximum value
seen:

    $ perl measurements.pl | stats | grep users | head
    found [4] of [16] users,
    found [1,1…4] of [12,12…16] users,
    found [1,1…4] of [16,12…16] users,
    found [0,0…4] of [6,6…16] users,
    found [1,0…4] of [11,6…16] users,
    found [4,0…4] of [15,6…16] users,
    found [4,0…4] of [7,6…16] users,
    found [2,0…4] of [22,6…22] users,
    found [2,0…4] of [10,6…22] users,
    found [2,0…4] of [13,6…22] users,

It works with heterogenous lines of data; the statistics are combined
only with other similar lines ("similar" meaning "same text, different
numbers"):

    $ perl measurements.pl | stats | egrep 'users|time' | head
    found [4] of [16] users,
     %time [174.780815] + [0.745095]
    found [1,1…4] of [12,12…16] users,
    Status count: [8449] of blue
     %time [143.107719,143.107719…174.780815] + [0.083004,0.083004…0.745095]
    found [1,1…4] of [16,12…16] users,
     %time [55.101785,55.101785…174.780815] + [0.260361,0.083004…0.745095]
    found [0,0…4] of [6,6…16] users,
     %time [76.436485,55.101785…174.780815] + [0.154737,0.083004…0.745095]
    found [1,0…4] of [11,6…16] users,

You can reset the statistics when a sentinel value (`--reset`) is
detected (notice that when the `Test run.*starting` regex matches, the
stats reset):

    $ perl measurements.pl | stats -r 'Test run.*starting' | egrep 'green|Test' | head -15
    Status count: [4360] of green
    >>> Test run #[0], starting now...
    >>> Test run #[1], starting now...
    Status count: [9672] of green
    Status count: [2177,2177…9672] of green
    >>> Test run #[2], starting now...
    >>> Test run #[3], starting now...
    >>> Test run #[4], starting now...
    >>> Test run #[5], starting now...
    Status count: [3869] of green
    >>> Test run #[6], starting now...
    Status count: [2653] of green
    Status count: [1590,1590…2653] of green
    Status count: [2743,1590…2743] of green
    Status count: [57,57…2743] of green

You can include the average value as well:

    $ perl measurements.pl | stats -a | grep 'green' | head
    Status count: [4360] of green
    Status count: [9672,4360…9672,μ=7016.0] of green
    Status count: [2177,2177…9672,μ=5403.0] of green
    Status count: [3869,2177…9672,μ=5019.5] of green
    Status count: [2653,2177…9672,μ=4546.2] of green
    Status count: [1590,1590…9672,μ=4053.5] of green
    Status count: [2743,1590…9672,μ=3866.3] of green
    Status count: [57,57…9672,μ=3390.1] of green
    Status count: [8602,57…9672,μ=3969.2] of green
    Status count: [1247,57…9672,μ=3697.0] of green

And the sample size, too:

    $ perl measurements.pl | stats -c | grep 'green' | head
    Status count: [4360] of green
    Status count: [9672,4360…9672,#=2] of green
    Status count: [2177,2177…9672,#=3] of green
    Status count: [3869,2177…9672,#=4] of green
    Status count: [2653,2177…9672,#=5] of green
    Status count: [1590,1590…9672,#=6] of green
    Status count: [2743,1590…9672,#=7] of green
    Status count: [57,57…9672,#=8] of green
    Status count: [8602,57…9672,#=9] of green
    Status count: [1247,57…9672,#=10] of green

## Compiling

Install scala-native & its dependencies, then run `sbt nativeLink`.

## Installing

Link the binary somewhere on your path; I do something akin to:

    ln -s $(realpath target/scala-2.11/stats-out) $(realpath ~/bin)
    