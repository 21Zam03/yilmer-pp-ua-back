package com.zam.sunat_service.service;

import com.zam.sunat_service.request.ClientRequest;
import com.zam.sunat_service.response.ClientResponse;
import com.zam.sunat_service.response.ClientStatsResponse;
import com.zam.sunat_service.response.PageResponse;

public interface ClientService {

    PageResponse<ClientResponse> getClients(String searchKey, Boolean active, int page, int size);
    ClientStatsResponse getStats();
    ClientResponse getClient(Integer clientId);
    ClientResponse createClient(ClientRequest request);
    ClientResponse updateClient(Integer clientId, ClientRequest request);
    ClientResponse updateStatus(Integer clientId, boolean enabled);
    void deleteClient(Integer clientId);

}
