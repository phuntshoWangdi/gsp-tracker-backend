#!/bin/sh

PRESERVECONFIG=0
if [ -f /opt/fasttrack/conf/fasttrack.xml ]
then
    cp /opt/fasttrack/conf/fasttrack.xml /opt/fasttrack/conf/fasttrack.xml.saved
    PRESERVECONFIG=1
fi

mkdir -p /opt/fasttrack
cp -r * /opt/fasttrack
chmod -R go+rX /opt/fasttrack

if [ ${PRESERVECONFIG} -eq 1 ] && [ -f /opt/fasttrack/conf/fasttrack.xml.saved ]
then
    mv -f /opt/fasttrack/conf/fasttrack.xml.saved /opt/fasttrack/conf/fasttrack.xml
fi

mv /opt/fasttrack/fasttrack.service /etc/systemd/system
chmod 664 /etc/systemd/system/fasttrack.service

systemctl daemon-reload
systemctl enable fasttrack.service

rm /opt/fasttrack/setup.sh
rm -r ../out
