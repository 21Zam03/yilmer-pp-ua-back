package com.zam.sunat_service.mapper;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.request.ClientRequest;
import com.zam.sunat_service.response.ClientResponse;

public class ClientMapper {

    private ClientMapper() {
    }

    // No toca la clave SOL: el service la cifra y asigna por separado.
    public static void requestToEntity(ClientRequest request, ClientEntity entity) {
        entity.setRuc(request.getRuc().trim());
        entity.setBusinessName(request.getBusinessName().trim());
        entity.setTradeName(blankToNull(request.getTradeName()));
        entity.setAddress(blankToNull(request.getAddress()));
        entity.setSolUser(request.getSolUser().trim().toUpperCase());
        entity.setContactName(blankToNull(request.getContactName()));
        entity.setPhoneNumber(blankToNull(request.getPhoneNumber()));
        entity.setEmail(blankToNull(request.getEmail()));
    }

    public static ClientResponse entityToResponse(ClientEntity entity) {
        ClientResponse response = new ClientResponse();
        response.setClientId(entity.getClientId());
        response.setRuc(entity.getRuc());
        response.setBusinessName(entity.getBusinessName());
        response.setTradeName(entity.getTradeName());
        response.setAddress(entity.getAddress());
        response.setSolUser(entity.getSolUser());
        response.setHasSolPassword(entity.getSolPassword() != null && !entity.getSolPassword().isEmpty());
        response.setSunatClientId(entity.getSunatClientId());
        response.setHasSunatClientSecret(entity.getSunatClientSecret() != null && !entity.getSunatClientSecret().isEmpty());
        response.setContactName(entity.getContactName());
        response.setPhoneNumber(entity.getPhoneNumber());
        response.setEmail(entity.getEmail());
        response.setEnabled(entity.isEnabled());
        response.setCreatedDate(entity.getCreatedDate());
        response.setUpdatedDate(entity.getUpdatedDate());
        return response;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
