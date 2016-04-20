#! /usr/bin/python

import optparse
import subprocess
import glob
from subprocess import Popen


def parse_args():
    option_list = [
        optparse.make_option('-v', dest='verbose', action='store_true', default=False, help='print all information'),
        optparse.make_option('-k', dest='keep_jar', action='store_true', default=False,
                             help='keep generated small.jar'),
        optparse.make_option('-d', dest='asset_dir', action='store', help='asset directory'),
        optparse.make_option('-p', dest='package_name', action='store', help='target package name')]
    parser = optparse.OptionParser(usage='Usage: %prog [-v] -p package_name', option_list=option_list)

    return parser.parse_args()


def run(*cmd):
    p = Popen(*cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    while True:
        line = p.stdout.readline()
        print line.replace('\n', '')
        if not line:
            break
    return p.wait()


if __name__ == '__main__':
    (opt, args) = parse_args()
    verbose = opt.verbose
    pkg = opt.package_name
    keep = opt.keep_jar
    asset_dir = opt.asset_dir
    if asset_dir[-1] == '/':
        asset_dir = asset_dir[0:-1]

    tasks = [['zip', '-j', 'small.jar', asset_dir + '/bundle.json'] + glob.glob(asset_dir + '/*.bundle'),
             ['adb', 'push', 'small.jar', '/data/data/' + pkg + '/files/small/upgrade/small.jar']]

    if not keep:
        tasks.append(['rm', '-rf', 'small.jar'])

    for task in tasks:
        if verbose:
            print 'executing: ' + ' '.join(task)
        if run(task):
            exit(1)
