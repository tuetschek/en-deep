#!/usr/bin/perl

use warnings;
use strict;


# process all files from the args
while (@ARGV > 0){
    my $file = shift;
    stats($file);
}


# stats about one ARFF file
sub stats {
    my ($file) = @_;

    open(IN, $file);
    binmode(IN, ":utf8");
    
    # relation name
    my $rel = <IN>;
    $rel =~ s/\@relation *//i;
    print($file. " --- " . $rel . "\n");

    # read all attributes
    my $numAttrib = 0;
    my ($numeric, $nominal) = ("", "");
    my $line;
    while (defined($line = <IN>) && $line !~ m/^\@data.*/i ){

        if ($line =~ m/^\@attribute.*/i){

            $line =~ s/\@attribute\s+//;

            my ($attrname, $type) = split(/\s+/, $line);
            
            if ($type =~ m/^{/){
                my @vals = split(/,/, $type);
                print($attrname . ":" . scalar(@vals));
                $nominal .= $numAttrib . " ";
            }
            else {
                print($attrname . ":" . $type);
                $numeric .= $numAttrib . " ";
            }                
            
            print(" ");
            $numAttrib++;
        }
    }
    print("\ntotal: " . $numAttrib . " attribs.\nnumeric:$numeric\nnominal:$nominal\n");
    close(IN);
}
