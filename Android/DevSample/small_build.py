#! /usr/bin/python
import subprocess

from subprocess import Popen


def run(*args):
    p = Popen(*args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    while True:
        line = p.stdout.readline()
        print line.replace('\n', '')
        if not line:
            break
    return p.wait()


if __name__ == '__main__':
    tasks = list()
    tasks.append(['gradle', 'clean', 'cleanLib', 'cleanBundle'])
    tasks.append(['gradle', 'buildLib'])
    tasks.append(['gradle', 'buildBundle'])
    tasks.append(['gradle', ':app:aR'])
    for task in tasks:
        if run(task):
            break
