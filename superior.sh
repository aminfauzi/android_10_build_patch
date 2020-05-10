
# Fix build error
git -C frameworks/av fetch https://github.com/aminfauzi/android_10_build_patch.git superior-frameworks-av && git -C frameworks/av cherry-pick b534c837635b2aca176bfbf126903e05c8809506 