
#Gallery2: Fix file still exist in SD card when delete from Gallery
git -C packages/apps/Gallery2 fetch https://github.com/SuperiorOS/android_packages_apps_Gallery2.git ten && git -C packages/apps/Gallery2 cherry-pick 8d8b01076d29fafaa757c6fbba2f7c261534e41b