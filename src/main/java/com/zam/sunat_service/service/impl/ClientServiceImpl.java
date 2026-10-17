package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.exceptions.DuplicateException;
import com.zam.sunat_service.exceptions.NotFoundException;
import com.zam.sunat_service.mapper.ClientMapper;
import com.zam.sunat_service.repository.AlertaRepository;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.request.ClientRequest;
import com.zam.sunat_service.response.ClientResponse;
import com.zam.sunat_service.response.ClientStatsResponse;
import com.zam.sunat_service.response.PageResponse;
import com.zam.sunat_service.service.ClientService;
import com.zam.sunat_service.util.SolPasswordEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientServiceImpl implements ClientService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClientRepository clientRepository;
    private final SolPasswordEncryptor solPasswordEncryptor;
    private final AlertaRepository alertaRepository;

    @Autowired
    public ClientServiceImpl(ClientRepository clientRepository, SolPasswordEncryptor solPasswordEncryptor,
                             AlertaRepository alertaRepository) {
        this.clientRepository = clientRepository;
        this.solPasswordEncryptor = solPasswordEncryptor;
        this.alertaRepository = alertaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> getClients(String searchKey, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdDate")
        );
        String key = searchKey == null || searchKey.isBlank() ? null : searchKey.trim();
        return PageResponse.from(
                clientRepository.findByFilters(key, active, pageable).map(ClientMapper::entityToResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ClientStatsResponse getStats() {
        long active = clientRepository.countByEnabled(true);
        long inactive = clientRepository.countByEnabled(false);
        return new ClientStatsResponse(active + inactive, active, inactive);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getClient(Integer clientId) {
        return ClientMapper.entityToResponse(findClient(clientId));
    }

    @Override
    @Transactional
    public ClientResponse createClient(ClientRequest request) {
        if (clientRepository.existsByRuc(request.getRuc().trim())) {
            throw new DuplicateException("Ya existe un cliente registrado con el RUC " + request.getRuc());
        }
        if (request.getSolPassword() == null || request.getSolPassword().isBlank()) {
            throw new BusinessException("La clave SOL es obligatoria");
        }

        ClientEntity client = new ClientEntity();
        ClientMapper.requestToEntity(request, client);
        client.setSolPassword(solPasswordEncryptor.encrypt(request.getSolPassword()));
        applyApiCredentials(request, client);
        client.setEnabled(true);
        return ClientMapper.entityToResponse(clientRepository.save(client));
    }

    @Override
    @Transactional
    public ClientResponse updateClient(Integer clientId, ClientRequest request) {
        ClientEntity client = findClient(clientId);
        if (clientRepository.existsByRucAndClientIdNot(request.getRuc().trim(), clientId)) {
            throw new DuplicateException("Ya existe otro cliente registrado con el RUC " + request.getRuc());
        }

        String previousRuc = client.getRuc();
        String previousCredentials = client.getRuc() + client.getSolUser();
        ClientMapper.requestToEntity(request, client);
        // Las alertas guardan el RUC (no una FK): se mueven al nuevo RUC para no quedar huérfanas.
        if (!previousRuc.equals(client.getRuc())) {
            alertaRepository.updateClienteRuc(previousRuc, client.getRuc());
        }
        boolean credentialsChanged = !previousCredentials.equals(client.getRuc() + client.getSolUser());
        // Clave SOL vacía al editar = conservar la actual.
        if (request.getSolPassword() != null && !request.getSolPassword().isBlank()) {
            client.setSolPassword(solPasswordEncryptor.encrypt(request.getSolPassword()));
            credentialsChanged = true;
        }
        if (applyApiCredentials(request, client)) {
            credentialsChanged = true;
        }
        // El token SUNAT en caché se generó con las credenciales anteriores.
        if (credentialsChanged) {
            client.setAccessToken(null);
            client.setTokenExpiraEl(null);
        }
        return ClientMapper.entityToResponse(clientRepository.save(client));
    }

    @Override
    @Transactional
    public ClientResponse updateStatus(Integer clientId, boolean enabled) {
        ClientEntity client = findClient(clientId);
        client.setEnabled(enabled);
        return ClientMapper.entityToResponse(clientRepository.save(client));
    }

    @Override
    @Transactional
    public void deleteClient(Integer clientId) {
        ClientEntity client = findClient(clientId);
        // Tickets, ventas y programaciones se borran por ON DELETE CASCADE; las alertas no tienen FK.
        alertaRepository.deleteByClienteRuc(client.getRuc());
        clientRepository.delete(client);
    }

    /**
     * Asigna el client_id / client_secret de la API SUNAT. Devuelve true si cambiaron.
     * client_id vacío = quitar ambas credenciales; client_secret vacío = conservar el guardado (si existe).
     */
    private boolean applyApiCredentials(ClientRequest request, ClientEntity client) {
        String newClientId = request.getSunatClientId() == null || request.getSunatClientId().isBlank()
                ? null : request.getSunatClientId().trim();
        String newSecret = request.getSunatClientSecret() == null || request.getSunatClientSecret().isBlank()
                ? null : request.getSunatClientSecret().trim();

        if (newClientId == null) {
            if (newSecret != null) {
                throw new BusinessException("Ingresa el client_id de la API SUNAT junto con el client_secret");
            }
            boolean hadCredentials = client.getSunatClientId() != null || client.getSunatClientSecret() != null;
            client.setSunatClientId(null);
            client.setSunatClientSecret(null);
            return hadCredentials;
        }

        if (newSecret == null && client.getSunatClientSecret() == null) {
            throw new BusinessException("El client_secret de la API SUNAT es obligatorio cuando se indica el client_id");
        }

        boolean changed = !newClientId.equals(client.getSunatClientId());
        client.setSunatClientId(newClientId);
        if (newSecret != null) {
            client.setSunatClientSecret(solPasswordEncryptor.encrypt(newSecret));
            changed = true;
        }
        return changed;
    }

    private ClientEntity findClient(Integer clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("No se encontró el cliente con id " + clientId));
    }

}
