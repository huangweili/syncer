#!/usr/bin/env bash


while :;do
        res=`docker-compose -f mysql.yml mysql_0 mysql -uroot -root test_0 -N -s -e "select count(1) from news;select count(1) from news_bak;" | tr -s 'A-Z' 'a-z' | grep -v "warn" | xargs`
        src=`echo $res | awk '{print $1}'`
        dst=`echo $res | awk '{print $1}'`
        echo "src=$src"
        echo "dst=$dst"
        sleep 5
done