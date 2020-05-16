
# Fix Miracast for devices using HDCP method
git -C frameworks/av fetch https://github.com/aminfauzi/android_10_build_patch.git superior-frameworks-av && git -C frameworks/av cherry-pick 6cd12c14cc71c10c8b2eaf76de0294cbea690ea2

# Fix RIL on Sprint & Verizon  (no need for superior)
git -C packages/providers/TelephonyProvider fetch https://github.com/aminfauzi/android_10_build_patch.git TelephonyProvider && git -C packages/providers/TelephonyProvider cherry-pick a1e2b2e355f47aadea78599a746081ebaebc73bd