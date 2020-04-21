#!/usr/bin/perl

sub rr {
  my ($max) = @_;
  int(rand($max))
}

sub m0 {
  printf("%dms running, size: %d\n", rr(100000), rr(200));
}
sub m1 {
  printf("found %d of %d users,\n  %dkb\n", rr(5), 5 + rr(20), rr(1024*1024));
}
sub m2 {
  my @words = qw{one two three four five six seven eight nine ten red blue
                 green yellow purple orange banana apple pineapple horse};
  printf("Status count: %d of %s\n", rr(9999), $words[rr(scalar @words)]);
}

sub decimal {
  printf(" %%time %f + %f\n", rand(200), rand());
}

while (1) {
  m0;
  m1;
  m2;
  decimal;
}
