
# Fix home button wake device
git -C frameworks/base fetch https://github.com/aminfauzi/aicp_frameworks_base q10.0 && git -C frameworks/base cherry-pick 4bd28797c8fc2ed66f630381e0abc4af9bd50ada
git -C frameworks/base fetch https://github.com/aminfauzi/aicp_frameworks_base q10.0 && git -C frameworks/base cherry-pick 375e4cc2a2f5f9ff178bba69e2b0f4e9f03f87d0

# Fix Miracast for devices using HDCP method
git -C frameworks/av fetch https://github.com/aminfauzi/android_10_build_patch.git superior-frameworks-av && git -C frameworks/av cherry-pick 6cd12c14cc71c10c8b2eaf76de0294cbea690ea2