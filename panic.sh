#!/bin/sh

SERVICE=$1

curl -H "Content-Type: application/json" -d "{\"source\":\"monit\",\"from_address\":\"monit@passenger.me\",\"subject\":\"$SERVICE check fails\",\"content\":\"monit check fails\",\"tags\":[\"$SERVICE\",\"monit\"]}" https://api.flowdock.com/v1/messages/team_inbox/93317bef88fb244e76a012d20484dbdc
