#!/usr/bin/env perl
#
# Move logs of finished jobs to the logs/ directory
#


use strict;
use warnings;
use File::Copy;

use constant LOGS_DIR => "logs/";

if (@ARGV < 1){
    die("Usage: bin/clear_logs.pl experiment1 [experiment2 ...]");
}


my @jobs = `qstat`;
shift @jobs; shift @jobs;

@jobs = map { $_ =~ s/^\s*(\S*)\s.*\r?\n$/$1/; $_ } @jobs;
my $jobs_regexp = join '|', @jobs;

foreach my $experiment (@ARGV){
    
    my @files = glob("$experiment.*.e* $experiment.*.o*");
    @files = grep { $_ !~ /^$experiment\..*\.[eo]($jobs_regexp)$/ } @files;

    foreach my $file (@files){
        move($file, LOGS_DIR);
    }
}
