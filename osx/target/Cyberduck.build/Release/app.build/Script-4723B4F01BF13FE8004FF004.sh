#!/bin/sh
sed -i '.tmp' 's/${SPARKLE_KEY_PREFIX}/'${SPARKLE_KEY_PREFIX}'/g' "${TARGET_BUILD_DIR}/${INFOPLIST_PATH}"
rm "${TARGET_BUILD_DIR}/${INFOPLIST_PATH}".tmp