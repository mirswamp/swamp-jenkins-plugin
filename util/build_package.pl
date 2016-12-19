#!/usr/bin/perl

#  build_package.pl
#
#  Copyright 2016 Jared Sweetland, Vamshi Basupalli, James A. Kupsch
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.


use strict;
use warnings;
use Getopt::Long;
use File::Copy;
#use Archive::Zip;
use IO::Compress::Zip qw(:all);
use File::Path 'rmtree';

sub ProcessOptions {

        my %options = (
                        help            => 0,
                        version         => 0,
			no_zip		=> 0,
			set_version	=> '0.6'
                        );

        my @options = (
                        "help|h",
                        "version|v",
			"no_zip|no-zip|z",
			"set_version|set-version|s=s"
                        );

        my $ok = GetOptions(\%options, @options);

        if (!$ok || $options{help})  {
                PrintUsage();
                exit !$ok;
        }

        if ($options{version})  {
                PrintVersion();
                exit 0;
        }

        return \%options;

}

sub PrintUsage {
        my $progname = $0;
        $progname =~ s/.*[\\\/]//;

        print STDERR <<EOF;
Usage: $progname --options <arguments>...

Packages the Jenkins plugin into an hpi file along with the README and RELEASE_NOTES in a new directory
    options:
        --help            -h print this message
        --version         -v print version of $progname
	--no-zip	  -z produces a directory rather than a zip file
	--set-version	  -s sets the version name for the package (only used in naming the directory)
EOF
}

sub PrintVersion {
        my $progname =  $0;
        $progname =~ s/.*[\\\/]//;
        my $version = "0.1";
        print STDERR "$progname\nVersion $version";
}

sub main {
        my $options = ProcessOptions();
	my $packageName = "SWAMP-Jenkins-v$options->{set_version}";
	if (!$options->{no_zip} && -e "$packageName.zip"){
		die "Could not create zip file: $packageName.zip already exists.\nStopping";
	}
	system("/p/swamp/bin/asciidoctor README.adoc -b html") and die "Could not create html from README.adoc - $!\nStopping";
	mkdir "$packageName" or die "Could not create directory $packageName: $!\nStopping";
	move("README.html","$packageName/") or die "Could not move README.html to $packageName/ - $!\nStopping";
	system("/p/swamp/bin/asciidoctor README.adoc") and die "Could not create pdf from README.adoc - $!\nStopping";
	move("README.pdf","$packageName/") or die "Could not move README.pdf to $packageName/ - $!\nStopping";
	copy("RELEASE_NOTES.txt","$packageName/") or die "Could not copy RELEASE_NOTES.txt to $packageName/ - $!\nStopping";
	chdir "Swamp";
	my $retCode = system "mvn package -Denforcer.skip=true -DskipTests";
	if ($retCode){
		print "Packaging failed - $!\n";
	}
	chdir "..";
	move("Swamp/target/Swamp.hpi", "$packageName/$packageName.hpi") or die "Could not move $packageName.hpi - $!\nStopping";
	if (!$options->{no_zip}){
		my $retCode = zip [ glob("$packageName/*") ] => "$packageName.zip" or die "Cannot create zip file: $ZipError" ;
		rmtree(["$packageName/"]) or die "Could not remove contents of $packageName during zipping process: $!\nStopping";
	}
}

main();

