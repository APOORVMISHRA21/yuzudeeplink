/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.adblock.ui.original

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import jp.hazuki.yuzubrowser.adblock.R
import jp.hazuki.yuzubrowser.adblock.databinding.FragmentAdBlockImportBinding
import jp.hazuki.yuzubrowser.adblock.filter.fastmatch.AdBlockDecoder
import jp.hazuki.yuzubrowser.adblock.repository.original.AdBlock
import jp.hazuki.yuzubrowser.core.utility.utils.ui
import kotlinx.android.extensions.CacheImplementation
import kotlinx.android.extensions.ContainerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

@ContainerOptions(CacheImplementation.NO_CACHE)
class AdBlockImportFragment : Fragment() {

    private var listener: OnImportListener? = null

    private val viewModel by viewModels<AdBlockImportViewModel>()

    private var viewBinding: FragmentAdBlockImportBinding? = null

    private val binding: FragmentAdBlockImportBinding
        get() = viewBinding!!

    private lateinit var okCallback: () -> Unit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = FragmentAdBlockImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity ?: return
        val fragmentManager = parentFragmentManager
        val uri = arguments?.getParcelable<Uri>(ARG_URI) ?: throw IllegalArgumentException()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        viewModel.event.observe(viewLifecycleOwner, this::onButtonClick)

        var input: ByteArray? = null
        try {
            input = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (input != null && input.size > EDITABLE_SIZE) {
            viewModel.text *= getString(R.string.adblock_file_large_mes)
            binding.editText.keyListener = null
            viewModel.isExclude *= false

            okCallback = {
                ui {
                    viewModel.text *= getString(R.string.now_loading)
                    viewModel.isButtonEnable *= false
                    val adBlocks = withContext(Dispatchers.Default) {
                        AdBlockDecoder.decode(Scanner(ByteArrayInputStream(input)), true)
                    }
                    listener!!.onImport(adBlocks)
                    parentFragmentManager.popBackStack()
                }
            }
        } else {
            viewModel.text *= input?.toString(StandardCharsets.UTF_8) ?: ""

            okCallback = {
                val adBlocks = AdBlockDecoder.decode(viewModel.text.value, viewModel.isExclude.value)
                listener!!.onImport(adBlocks)
                fragmentManager.popBackStack()
            }
        }
    }

    private fun onButtonClick(event: Int) {
        when (event) {
            AdBlockImportViewModel.EVENT_OK -> {
                okCallback()
            }
            AdBlockImportViewModel.EVENT_CANCEL -> {
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = activity as OnImportListener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    interface OnImportListener {
        fun onImport(adBlocks: List<AdBlock>)
    }

    companion object {
        private const val ARG_URI = "uri"

        private const val EDITABLE_SIZE = 1024 * 1024

        operator fun invoke(uri: Uri): AdBlockImportFragment {
            return AdBlockImportFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_URI, uri)
                }
            }
        }
    }
}
