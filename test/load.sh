#!/usr/bin/env bash

docker-compose -f mysql.yml mysql_0 mysql -uroot -root test_0 -N -s -e "delete from news;delete from news_bak;"
for file in data/csvs/;do
        cp $file data/news.csv
        docker-compose -f mysql.yml exec mysql_0 mysqlimport --fields-terminated-by=, --verbose --local -uroot -proot test_0 /Data/news.csv
done
rm -f data/news.csv