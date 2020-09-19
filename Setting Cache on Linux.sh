Installation:

# Install package
sudo apt install -y ccache

# Update symlinks
sudo /usr/sbin/update-ccache-symlinks

# Prepend ccache into the PATH
echo 'export PATH="/usr/lib/ccache:$PATH"' | tee -a ~/.bashrc

# Source bashrc to test the new PATH
source ~/.bashrc && echo $PATH

Your path (at least the beginning) should look like:

/usr/lib/ccache:/usr/local/cuda-5.5/bin/:/usr/lib/lightdm/lightdm:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games

And g++/gcc should now point to:

which g++ gcc
/usr/lib/ccache/g++
/usr/lib/ccache/gcc

Configuration:

If you want no limit to the number of files and size of the cache:

ccache -F 0
ccache -M 0

Show cache statistics:

ccache -s

Empty the cache and reset the stats:

ccache -C -z

Usage:

Every time you call gcc or g++; ccache is called. My mistake was that I didn't delete already-compiled files. Just delete all your CMake/output files and configure/compile again.

Your ccache shouldn't be empty then. Now try a make clean and make and you'll see it is much faster than re-compiling everything thanks to the cache.
