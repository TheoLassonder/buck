#!/usr/bin/env python

import errno
import glob
import json
import os
import os.path
import platform
import time
from timing import monotonic_time_nanos
import uuid

def create_symlink(original, symlink):
    if platform.system() == 'Windows':
        # Not worth dealing with the convenience symlink on Windows.
        return
    else:
        (symlink_dir, symlink_file) = os.path.split(symlink)
        # Avoid race conditions with other processes by:
        #
        # 1) Creating a symlink /path/to/.symlink_file.UUID -> /path/to/original
        # 2) Atomically renaming /path/to/.symlink_file.UUID -> /path/to/symlink_file
        #
        # If another process races with this one, the most recent one wins, which
        # is the behavior we want.
        temp_symlink_filename = ".{0}.{1}".format(symlink_file, uuid.uuid4())
        temp_symlink_path = os.path.join(symlink_dir, temp_symlink_filename)
        os.symlink(original, temp_symlink_path)
        os.rename(temp_symlink_path, symlink)

class _TraceEventPhases(object):
    BEGIN = 'B'
    END = 'E'
    IMMEDIATE = 'I'
    COUNTER = 'C'
    ASYNC_START = 'S'
    ASYNC_FINISH = 'F'
    OBJECT_SNAPSHOT = 'O'
    OBJECT_NEW = 'N'
    OBJECT_DELETE = 'D'
    METADATA = 'M'


class Tracing(object):
    _trace_events = [
        {
            'name': 'process_name',
            'ph': _TraceEventPhases.METADATA,
            'pid': os.getpid(),
            'args': {'name': 'buck.py'}
        }
    ]

    def __init__(self, name, args={}):
        self.name = name
        self.args = args
        self.pid = os.getpid()

    def __enter__(self):
        now_us = monotonic_time_nanos() / 1000
        self._add_trace_event(
            'buck-launcher',
            self.name,
            _TraceEventPhases.BEGIN,
            self.pid,
            1,
            now_us,
            self.args)

    def __exit__(self, x_type, x_value, x_traceback):
        now_us = monotonic_time_nanos() / 1000
        self._add_trace_event(
            'buck-launcher',
            self.name,
            _TraceEventPhases.END,
            self.pid,
            1,
            now_us,
            self.args)

    @staticmethod
    def _add_trace_event(
            category,
            name,
            phase,
            pid,
            tid,
            ts,
            args):
        Tracing._trace_events.append({
            'cat': category,
            'name': name,
            'ph': phase,
            'pid': pid,
            'tid': tid,
            'ts': ts,
            'args': args})

    @staticmethod
    def write_to_dir(buck_log_dir, build_id):
        filename_time = time.strftime('%Y-%m-%d.%H-%M-%S')
        trace_filename = os.path.join(
            buck_log_dir, 'launch.{0}.{1}.trace'.format(filename_time, build_id))
        trace_filename_link = os.path.join(buck_log_dir, 'launch.trace')
        try:
            os.makedirs(buck_log_dir)
        except OSError as e:
            if e.errno != errno.EEXIST:
                raise
        with open(trace_filename, 'w') as f:
            json.dump(Tracing._trace_events, f)

        create_symlink(trace_filename, trace_filename_link)
        Tracing.clean_up_old_logs(buck_log_dir)

    @staticmethod
    def clean_up_old_logs(buck_log_dir, logs_to_keep=25):
        traces = filter(os.path.isfile, glob.glob(os.path.join(buck_log_dir, 'launch.*.trace')))
        traces = sorted(traces, key=os.path.getmtime)
        for f in traces[:-logs_to_keep]:
            os.remove(f)
