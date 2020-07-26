import psutil
import time
import socket
import csv

hostname = socket.gethostname()

with open("/filestore/perf_{host}.csv".format(host=hostname), 'w') as logfile:
    logwriter = csv.writer(logfile)
    logwriter.writerow(["Processor %", "Memory Free %",
                        "Disk Read", "Disk Write"])
    disk_write_baseline = psutil.disk_io_counters().write_count
    disk_read_baseline = psutil.disk_io_counters().read_count
    while True:
        logwriter = csv.writer(logfile)
        logwriter.writerow([
            psutil.cpu_percent(),
            psutil.virtual_memory().percent,
            psutil.disk_io_counters().read_count - disk_read_baseline,
            psutil.disk_io_counters().write_count - disk_write_baseline
        ])
        time.sleep(1)
