#!/bin/sh

ssh \
-L 2501:openflow3.stanford.edu:2503 \
-L 2502:openflow6.stanford.edu:2503 \
-L 2503:openflow5.stanford.edu:2504 \
-L 2504:openflow8.stanford.edu:5432 \
-L 2505:openflow5.stanford.edu:2506 \
-L 2506:openflow5.stanford.edu:2507 \
-L 2507:openflow5.stanford.edu:2505 \
yuba.stanford.edu 'while `true` ; do date ; sleep 20 ; done '
#-L 2504:openflow4.stanford.edu:2503 \
