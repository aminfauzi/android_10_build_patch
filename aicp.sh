
# Fix home button wake device
git -C frameworks/base fetch https://github.com/aminfauzi/aicp_frameworks_base q10.0 && git -C frameworks/base cherry-pick 4bd28797c8fc2ed66f630381e0abc4af9bd50ada
git -C frameworks/base fetch https://github.com/aminfauzi/aicp_frameworks_base q10.0 && git -C frameworks/base cherry-pick 375e4cc2a2f5f9ff178bba69e2b0f4e9f03f87d0