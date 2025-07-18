import pandas as pd
import urllib.request as req
import urllib.parse
import datetime
import sys

class QueryBuilder:
    def __init__(self, baseUrl):
        self.baseUrl = baseUrl
        self.queryParams = {}

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        pass 

    def rangeQuery(self, query: str):
        self.baseUrl += "/api/v1/query_range"
        self.queryParams['query'] = query
        return self

    def since(self, date: str):
        self.queryParams['start'] = date
        return self

    def until(self, date: str):
        self.queryParams['end'] = date
        return self

    def step(self, step: int):
        self.queryParams['step'] = step
        return self

    def build(self):
        return self.baseUrl + "?" + urllib.parse.urlencode(self.queryParams)

url = ""

params = {
        'days': 1,
        'res': '30s',
        'steps': 45
}
key = ''
for arg in sys.argv:
    if ('--' in arg):
        key = arg[2:]
    elif (len(key) > 0):
        params[key] = arg

with QueryBuilder("http://rrt-general-services.dcs.bbk.ac.uk:31697") as qb:
    since = datetime.datetime.strftime(datetime.datetime.now() - datetime.timedelta(days=int(params['days'])), "%G-%m-%dT%H:%M:%SZ")
    until = datetime.datetime.strftime(datetime.datetime.now(), "%G-%m-%dT%H:%M:%SZ")
    url = qb.rangeQuery(f"sum by (instance) (rate(dwell_time_total[{params['res']}]))").since(since).until(until).step(params['steps']).build()
    try:
        res = req.urlopen(url)
        jsondata = res.read()
        df = pd.read_json(jsondata)
        print(df['data']['result'][15]['metric']['instance'])
        #print(df['data']['result'][0]['values'])

        df1 = pd.DataFrame()
        df1['Timestamp'] = list(map(lambda r: r[0], df['data']['result'][0]['values']))
        for i in range(0, 15):
            df1[df['data']['result'][i]['metric']['instance']] = list(map(lambda r: r[1], df['data']['result'][i]['values']))
        df1.to_csv(f"./perf-data-{until}-{params['days']}day.csv")

    except Exception as err:
        print(err.read())


