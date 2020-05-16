
# Fix build error
git -C frameworks/av fetch https://github.com/aminfauzi/android_10_build_patch.git superior-frameworks-av && git -C frameworks/av cherry-pick b534c837635b2aca176bfbf126903e05c8809506 

# Fix Miracast for devices using HDCP method
git -C frameworks/av fetch https://github.com/aminfauzi/android_10_build_patch.git superior-frameworks-av && git -C frameworks/av cherry-pick 6cd12c14cc71c10c8b2eaf76de0294cbea690ea2