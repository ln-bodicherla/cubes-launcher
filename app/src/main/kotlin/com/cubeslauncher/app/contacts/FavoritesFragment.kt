package com.cubeslauncher.app.contacts

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.app.databinding.FragmentContactsFavoritesBinding

class FavoritesFragment : Fragment() {

    private var _binding: FragmentContactsFavoritesBinding? = null
    private val binding get() = _binding!!
    private var adapter: FavoritesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.favoritesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadFavorites()
    }

    override fun onResume() {
        super.onResume()
        loadFavorites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        _binding = null
    }

    private fun loadFavorites() {
        val activity = requireActivity()
        if (activity is ContactsActivity && !activity.hasPermission(Manifest.permission.READ_CONTACTS)) {
            showEmpty()
            return
        }

        val repository = ContactsRepository(requireContext().contentResolver)
        val starred = repository.readStarredContacts()

        if (starred.isEmpty()) {
            showEmpty()
            return
        }

        if (adapter == null) {
            adapter = FavoritesAdapter(starred) { contact ->
                val intent = Intent(requireContext(), ContactDetailActivity::class.java).apply {
                    putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contact.id)
                }
                startActivity(intent)
            }
            binding.favoritesRecyclerView.adapter = adapter
        } else {
            adapter!!.updateContacts(starred)
        }
        binding.emptyState.visibility = View.GONE
        binding.favoritesRecyclerView.visibility = View.VISIBLE
    }

    private fun showEmpty() {
        binding.emptyState.visibility = View.VISIBLE
        binding.favoritesRecyclerView.visibility = View.GONE
    }
}
