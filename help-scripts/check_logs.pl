#!/usr/bin/env perl
#
# Check logs of running or all jobs (find last task line & the last overall line + print them for each log)
#
#


use strict;
use warnings;

if (@ARGV < 1){
    die("Usage: bin/check_logs.pl [-a] experiment1 [experiment2 ...]");
}

my $all = 0;
if ($ARGV[0] eq '-a'){
    $all = 1;
    shift @ARGV;
}

if ($all){
     foreach my $experiment (@ARGV){
        
        my @files = glob("$experiment.*.e*");

        foreach my $file (@files){
            check($file);
        }
    }
}
else {
    my @jobs = `qstat`;
    shift @jobs; shift @jobs;

    @jobs = map { $_ =~ s/^\s*(\S*)\s.*\r?\n$/$1/; $_ } @jobs;
    my $jobs_regexp = join '|', @jobs;

    foreach my $experiment (@ARGV){
        
        my @files = glob("$experiment.*.e*");
        @files = grep { $_ =~ /^$experiment\..*\.[eo]($jobs_regexp)$/ } @files;

        foreach my $file (@files){
            check($file);
        }
    }
}

# check a file: print out the last job-running status + last line
sub check {
    my ($file) = @_;
    my ($last_work, $last);

    open (my $fh, '<:utf8', $file);
    while (my $line = <$fh>){
        $last_work = $line if ($line =~ / --- ([^ ]+:|task)/);
        $last = $line if ($line =~ / --- /);
    }
    return if (!$last);
    if ($last_work && $last_work ne $last){
        print $file, ":\t", $last_work;
    }
    print $file, ":\t", $last;
    close($fh);
}
