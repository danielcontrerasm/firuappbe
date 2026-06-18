package com.example.pettracker.transit.service;

import com.example.pettracker.entity.User;
import com.example.pettracker.transit.entity.TransitVehicle;
import com.example.pettracker.transit.repository.TransitVehicleRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransitVehicleService {

    private final TransitVehicleRepository transitVehicleRepository;

    public TransitVehicleService(TransitVehicleRepository transitVehicleRepository) {
        this.transitVehicleRepository = transitVehicleRepository;
    }

    public List<TransitVehicle> listForUser(User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return transitVehicleRepository.findAll();
        }
        return transitVehicleRepository.findByOwnerId(user.getId());
    }

    public TransitVehicle findById(Long id) {
        return transitVehicleRepository.findById(id).orElse(null);
    }

    public TransitVehicle save(TransitVehicle vehicle) {
        return transitVehicleRepository.save(vehicle);
    }

    public boolean canAccess(User user, TransitVehicle vehicle) {
        return user.getRole() == User.Role.ADMIN
                || (vehicle.getOwner() != null
                && vehicle.getOwner().getId() != null
                && vehicle.getOwner().getId().equals(user.getId()));
    }
}
