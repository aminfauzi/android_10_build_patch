/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CODEC2_BUFFER_H_

#define CODEC2_BUFFER_H_

#include <C2Buffer.h>

#include <media/MediaCodecBuffer.h>

namespace android {

/**
 * MediaCodecBuffer implementation wraps around C2LinearBlock.
 */
class LinearBlockBuffer : public MediaCodecBuffer {
public:
    static sp<LinearBlockBuffer> allocate(
            const sp<AMessage> &format, const std::shared_ptr<C2LinearBlock> &block);

    virtual ~LinearBlockBuffer() = default;

    C2ConstLinearBlock share();

private:
    LinearBlockBuffer(
            const sp<AMessage> &format,
            C2WriteView &&writeView,
            const std::shared_ptr<C2LinearBlock> &block);
    LinearBlockBuffer() = delete;

    C2WriteView mWriteView;
    std::shared_ptr<C2LinearBlock> mBlock;
};

/**
 * MediaCodecBuffer implementation wraps around C2ConstLinearBlock.
 */
class ConstLinearBlockBuffer : public MediaCodecBuffer {
public:
    static sp<ConstLinearBlockBuffer> allocate(
            const sp<AMessage> &format, const C2ConstLinearBlock &block);

    virtual ~ConstLinearBlockBuffer() = default;

private:
    ConstLinearBlockBuffer(
            const sp<AMessage> &format,
            C2ReadView &&readView);
    ConstLinearBlockBuffer() = delete;

    C2ReadView mReadView;
};

}  // namespace android

#endif  // CODEC2_BUFFER_H_