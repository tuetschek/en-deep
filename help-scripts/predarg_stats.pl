#!/usr/bin/perl

use strict;
use warnings;

my @sentence;
my %preds;

while (my $line = <STDIN>){
    if ($line =~ /^$/){
        process_sentence();
        @sentence = ();
    }
    else {
        $line =~ s/\r?\n$//;
        push(@sentence, $line);
    }
}

print_stats();

#
# SUBS
#

sub process_sentence {

    my @preds = ();
    my @args = ();

    # gather all predicates and their arguments
    foreach my $word (@sentence){        
        my @fields = split(/\t/, $word);

        if ($fields[13] ne "_"){ # a predicate
            if ($fields[4] =~ /^N/){
                push(@preds, $fields[13] . ".n");
            }
            elsif ($fields[4] =~ /^V/){
                push(@preds, $fields[13] . ".v");
            }
            else {
                push(@preds, $fields[13] . ".e");
            }
        }
        for (my $i = 14; $i < @fields; ++$i){

            if ($fields[$i] ne "_"){ # an argument

                if (!$args[$i-14]){
                    $args[$i-14] = $fields[$i];
                }
                else {
                    $args[$i-14] .= " " . $fields[$i];
                }
            }
        }
    }

    # add them to the global statistics
    for (my $i = 0; $i < @preds; ++$i){

        my $pred = $preds[$i];        
        my @args = $args[$i] ? split(/\s+/, $args[$i]) : ();

        if (!$preds{$pred}){
            $preds{$pred} = {};
        }
        if (!$preds{$pred}->{"COUNT"}){
            $preds{$pred}->{"COUNT"} = 1;            
        }
        else {
            $preds{$pred}->{"COUNT"}++;
        }
        for my $arg (@args){
            if (!$preds{$pred}->{$arg}){
                $preds{$pred}->{$arg} = 1;
            }
            else {
                $preds{$pred}->{$arg}++;
            }
        }
    }
}

sub print_stats {

    for my $pred (keys(%preds)){
        
        print ($pred." -- ".$preds{$pred}->{"COUNT"}."x: ");
        delete($preds{$pred}->{"COUNT"});

        for my $arg (sort {$a cmp $b} keys(%{$preds{$pred}})){
            print($arg . " " . $preds{$pred}->{$arg} . " ");
        }
        print("\n");
    }
}
