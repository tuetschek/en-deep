#!/usr/bin/perl

use strict;
use warnings;

if (@ARGV != 3){
    die("Usage: ./create-predinfo.pl pred-arg-stats pb-frames nb-frames");
}

my ($pred_arg_stats, $pb_frames, $nb_frames) = @ARGV;

my %preds;

# read pred-arg stats
open(IN, $pred_arg_stats);
while (my $pred_arg = <IN>){
    $pred_arg =~ s/\s+$//;
    $pred_arg =~ m/(^[^ ]+) -- ([0-9]+)x: ?(.*)$/;
    my ($pred, $count, $stats) = ($1, $2, $3);
    $preds{$pred} = $count . ":" . $stats;
}
close(IN);

# add pb frames
open(IN, $pb_frames);
while (my $frame_info = <IN>){
    $frame_info =~ s/\s+$//;
    $frame_info =~ m/(^[^\[:]+)(\[[^\]]+\])?: ?(.*)$/;
    my ($pred, $frame) = ($1, $3);
    if (!$preds{$pred.".v"}){
        $preds{$pred.".v"} = "0:";
    }
    $preds{$pred.".v"} .= ":" . $frame;
}
close(IN);

# add nb frames
open(IN, $nb_frames);
while (my $frame_info = <IN>){
    $frame_info =~ s/\s+$//;
    $frame_info =~ m/(^[^\[:]+): ?(.*)$/;
    my ($pred, $frame) = ($1, $2);
    if (!$preds{$pred.".n"}){
        $preds{$pred.".n"} = "0:";
    }
    $preds{$pred.".n"} .= ":" . $frame;
}
close(IN);



foreach my $pred (sort {$a cmp $b} keys(%preds)){
    print($pred . ":" . $preds{$pred} . "\n");
}
