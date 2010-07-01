#!/usr/bin/perl

use warnings;
use strict;

while (@ARGV > 0){
    extract_frame(shift());
}

sub extract_frame {

    my ($file) = @_;
    my $first = 1;

    open(IN, $file);
    while (my $line = <IN>){

	if ($line =~ m/<roleset .*id="([^"]+)"(.* vncls="([^"]+)")?/){
	    
	    my $id = $1;
            my $vncls = $3 ? "[$3]" : "";
	    print(($first ? "" :  "\n") . $id . $vncls . ":");
	    $first = 0;
	}
	if ($line =~ m/<role .*f="([^"]+)".*n="([^"]+)"/){
	    if (uc($2) eq "M"){
		print(" AM-" . uc($1));
	    }
	    else {
		print(" A" . uc($2));
	    }
	}
	elsif ($line =~ m/<role .*n="([^"]+)"/){
	    print(" A" . uc($1));
	}
    }
    print("\n");
    close(IN);
}
